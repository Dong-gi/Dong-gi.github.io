#!/usr/bin/env node
// @ts-check
/**
 * fleet-topsql.mjs
 *
 * 계정 내 모든 DB 인스턴스에 대해 Performance Insights의
 * "부하 상위 쿼리 × 대기 이벤트" 교차 분석을 조회해 출력한다.
 *
 * AWS SDK 대신 CloudShell에 기본 설치된 aws CLI를 호출하므로 npm 설치가 필요 없다.
 *
 *   node fleet-topsql.mjs --region us-east-2
 *   node fleet-topsql.mjs --region us-east-2 --filter mahjong --limit 10
 *   node fleet-topsql.mjs --region us-east-2 --output tsv > topsql.tsv
 *   node fleet-topsql.mjs --region us-east-2 --probe --filter mahjong-instance-4
 */

import { execFile } from 'node:child_process';
import { parseArgs, promisify } from 'node:util';

const execFileAsync = promisify(execFile);

// ─────────────────────────────────────────────────────────────────────────────
// 상수
// ─────────────────────────────────────────────────────────────────────────────

/** describe-dimension-keys가 허용하는 집계 주기(초). 그 외 값은 API가 거부한다. */
const VALID_PERIODS = [1, 60, 300, 3600, 86400];

/** Performance Insights 무료 티어 기본 보존 기간(일). */
const DEFAULT_RETENTION_DAYS = 7;

const MAX_RETRY = 5;

/** 표 오른쪽 끝 쿼리 컬럼의 기본 표시 길이. */
const DEFAULT_STATEMENT_WIDTH = 280;

/** 대기 이벤트 컬럼 폭. 고정해야 오른쪽 쿼리 컬럼이 세로로 정렬된다. */
const DEFAULT_WAIT_WIDTH = 38;

const MINUTE = 60_000;
const DAY_MINUTES = 1440;

// ─────────────────────────────────────────────────────────────────────────────
// 인자 처리
// ─────────────────────────────────────────────────────────────────────────────

const USAGE = `
사용법: node fleet-topsql.mjs [옵션]

  --region, -r    리전. 쉼표로 여러 개 지정 가능 (기본: aws configure의 기본 리전)
  --profile, -p   AWS 프로파일
  --days, -d      조회 기간(일). 기본 7. 인스턴스별 PI 보존 기간을 넘지 않도록 자동 축소된다
  --limit, -l     인스턴스당 상위 쿼리 개수. 기본 5 (1~25)
  --waits, -w     대기 이벤트 파티션 개수. 기본 5 (1~25)
  --filter, -f    인스턴스 이름 부분 일치 필터
  --output, -o    table(기본) | tsv | json
  --top, -n       통합 요약에 표시할 쿼리 개수. 기본 10
  --period, -P    집계 주기(초)를 직접 지정: ${VALID_PERIODS.join(' | ')}
  --concurrency   동시 조회 인스턴스 수. 기본 4
  --sql-width     쿼리 컬럼 표시 길이. 기본 ${DEFAULT_STATEMENT_WIDTH}
  --wait-width    대기 이벤트 컬럼 폭. 기본 ${DEFAULT_WAIT_WIDTH}
  --quiet, -q     데이터가 없는 인스턴스는 생략
  --verbose, -v   요청 구간과 응답 요약을 stderr로 출력
  --probe         구간 길이·period 조합별로 응답이 비는 지점을 찾는 진단 모드
  --help, -h      이 도움말
`.trim();

const inRange = (n, min, max) => Number.isInteger(n) && n >= min && n <= max;

function parseCliArgs() {
  const { values } = parseArgs({
    options: {
      region: { type: 'string', short: 'r' },
      profile: { type: 'string', short: 'p' },
      days: { type: 'string', short: 'd', default: '7' },
      limit: { type: 'string', short: 'l', default: '5' },
      waits: { type: 'string', short: 'w', default: '5' },
      filter: { type: 'string', short: 'f', default: '' },
      output: { type: 'string', short: 'o', default: 'table' },
      top: { type: 'string', short: 'n', default: '10' },
      period: { type: 'string', short: 'P' },
      concurrency: { type: 'string', default: '4' },
      'sql-width': { type: 'string', default: String(DEFAULT_STATEMENT_WIDTH) },
      'wait-width': { type: 'string', default: String(DEFAULT_WAIT_WIDTH) },
      quiet: { type: 'boolean', short: 'q', default: false },
      verbose: { type: 'boolean', short: 'v', default: false },
      probe: { type: 'boolean', default: false },
      help: { type: 'boolean', short: 'h', default: false },
    },
  });

  if (values.help) {
    console.log(USAGE);
    process.exit(0);
  }

  const config = {
    regions: (values.region ?? '').split(',').map((s) => s.trim()).filter(Boolean),
    profile: values.profile,
    days: Number(values.days),
    sqlLimit: Number(values.limit),
    waitLimit: Number(values.waits),
    nameFilter: values.filter ?? '',
    output: values.output ?? 'table',
    summaryTop: Number(values.top),
    period: values.period === undefined ? undefined : Number(values.period),
    concurrency: Math.max(1, Number(values.concurrency)),
    statementWidth: Number(values['sql-width']),
    waitWidth: Number(values['wait-width']),
    quiet: values.quiet ?? false,
    verbose: values.verbose ?? false,
    probe: values.probe ?? false,
  };

  const fail = (message) => {
    console.error(`오류: ${message}\n\n${USAGE}`);
    process.exit(2);
  };

  if (!Number.isInteger(config.days) || config.days < 1) fail('--days 는 1 이상의 정수여야 합니다.');
  if (!inRange(config.sqlLimit, 1, 25)) fail('--limit 은 1~25 범위여야 합니다.');
  if (!inRange(config.waitLimit, 1, 25)) fail('--waits 는 1~25 범위여야 합니다.');
  if (!['table', 'tsv', 'json'].includes(config.output)) fail('--output 은 table, tsv, json 중 하나여야 합니다.');
  if (!inRange(config.statementWidth, 20, 4000)) fail('--sql-width 는 20~4000 범위여야 합니다.');
  if (!inRange(config.waitWidth, 10, 200)) fail('--wait-width 는 10~200 범위여야 합니다.');
  if (config.period !== undefined && !VALID_PERIODS.includes(config.period)) {
    fail(`--period 는 ${VALID_PERIODS.join(', ')} 중 하나여야 합니다.`);
  }
  return config;
}

// ─────────────────────────────────────────────────────────────────────────────
// AWS CLI 호출 계층
// ─────────────────────────────────────────────────────────────────────────────

const isThrottling = (message) => /throttl|rate exceeded|too many requests/i.test(message);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * aws CLI를 실행해 JSON 응답을 파싱한다. 스로틀링에만 지수 백오프로 재시도한다.
 * @param {string[]} args 예: ['rds', 'describe-db-instances']
 * @param {{region?: string, profile?: string}} ctx
 */
async function awsJson(args, ctx) {
  const fullArgs = [...args, '--output', 'json'];
  if (ctx.region) fullArgs.push('--region', ctx.region);
  if (ctx.profile) fullArgs.push('--profile', ctx.profile);

  for (let attempt = 1; ; attempt += 1) {
    try {
      const { stdout } = await execFileAsync('aws', fullArgs, { maxBuffer: 64 * 1024 * 1024 });
      return JSON.parse(stdout);
    } catch (error) {
      const detail = String(error.stderr || error.message || '').trim();
      if (attempt < MAX_RETRY && isThrottling(detail)) {
        await sleep(attempt * 2000);
        continue;
      }
      throw new Error(detail.split('\n').slice(0, 3).join(' ').slice(0, 300));
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 조회 구간 계산
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 구간 길이에 맞는 집계 주기를 고른다.
 * period를 지정하지 않으면 PI가 100~200 포인트를 목표로 알아서 고르는데,
 * 그 값을 예측할 수 없으면 아래 정렬 보정도 계산할 수 없으므로 항상 명시한다.
 */
function pickPeriod(windowMinutes, override) {
  if (override !== undefined) return override;
  if (windowMinutes <= 180) return 60;
  if (windowMinutes <= DAY_MINUTES) return 300;
  return 3600;
}

/**
 * 조회 구간을 만든다.
 *
 * PI는 StartTime을 period 경계로 "내림" 정렬한다(AlignedStartTime <= StartTime).
 * 그런데 StartTime은 반드시 최근 7일 이내여야 한다. 보존 경계 바로 앞을 요청하면
 * 정렬 결과가 보존 구간 밖으로 밀려 빈 응답이 돌아온다.
 * 그래서 period 하나만큼(+5분) 여유를 두고 시작 시각을 잡는다.
 */
function buildWindow({ days, retentionDays, periodOverride, now = new Date() }) {
  const effectiveDays = Math.min(days, retentionDays);
  const requestedMinutes = effectiveDays * DAY_MINUTES;
  const period = pickPeriod(requestedMinutes, periodOverride);
  const marginMinutes = Math.ceil(period / 60) + 5;
  const windowMinutes = Math.max(requestedMinutes - marginMinutes, 5);

  return {
    start: new Date(now.getTime() - windowMinutes * MINUTE),
    end: now,
    period,
    effectiveDays,
    windowMinutes,
  };
}

const iso = (date) => date.toISOString().replace(/\.\d{3}Z$/, 'Z');

// ─────────────────────────────────────────────────────────────────────────────
// 도메인 조회
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @typedef {object} Instance
 * @property {string} name
 * @property {string} resourceId
 * @property {string} instanceClass
 * @property {string} cluster
 * @property {'writer'|'reader'|'-'} role
 * @property {number} retentionDays
 * @property {string} region
 */

/** PI가 켜져 있고 available 상태인 인스턴스만 반환한다. */
async function listInstances(region, ctx, nameFilter) {
  const [instanceResponse, clusterResponse] = await Promise.all([
    awsJson(['rds', 'describe-db-instances'], { ...ctx, region }),
    awsJson(['rds', 'describe-db-clusters'], { ...ctx, region }).catch(() => ({ DBClusters: [] })),
  ]);

  /** 클러스터 이름 -> writer 인스턴스 이름 집합 */
  const writers = new Map(
    (clusterResponse.DBClusters ?? []).map((cluster) => [
      cluster.DBClusterIdentifier,
      new Set((cluster.DBClusterMembers ?? [])
        .filter((member) => member.IsClusterWriter)
        .map((member) => member.DBInstanceIdentifier)),
    ]),
  );

  return (instanceResponse.DBInstances ?? [])
    .filter((db) => db.PerformanceInsightsEnabled === true)
    .filter((db) => db.DBInstanceStatus === 'available')
    .filter((db) => !nameFilter || db.DBInstanceIdentifier.includes(nameFilter))
    .map((db) => ({
      name: db.DBInstanceIdentifier,
      resourceId: db.DbiResourceId,
      instanceClass: db.DBInstanceClass,
      cluster: db.DBClusterIdentifier ?? '-',
      role: !db.DBClusterIdentifier
        ? '-'
        : writers.get(db.DBClusterIdentifier)?.has(db.DBInstanceIdentifier) ? 'writer' : 'reader',
      retentionDays: db.PerformanceInsightsRetentionPeriod ?? DEFAULT_RETENTION_DAYS,
      region,
    }));
}

/** 부하 상위 쿼리를 대기 이벤트로 다시 쪼갠 교차 분석 원본 응답. */
function fetchTopSql(instance, window, ctx, { sqlLimit, waitLimit }) {
  return awsJson([
    'pi', 'describe-dimension-keys',
    '--service-type', 'RDS',
    '--identifier', instance.resourceId,
    '--start-time', iso(window.start),
    '--end-time', iso(window.end),
    '--period-in-seconds', String(window.period),
    '--metric', 'db.load.avg',
    '--group-by', JSON.stringify({
      Group: 'db.sql_tokenized',
      Dimensions: ['db.sql_tokenized.id', 'db.sql_tokenized.statement'],
      Limit: sqlLimit,
    }),
    '--partition-by', JSON.stringify({ Group: 'db.wait_event', Limit: waitLimit }),
  ], { ...ctx, region: instance.region });
}

/** 인스턴스 전체 평균 DB Load. 상위 쿼리 비중의 분모로 쓴다. */
async function fetchTotalLoad(instance, window, ctx) {
  const response = await awsJson([
    'pi', 'get-resource-metrics',
    '--service-type', 'RDS',
    '--identifier', instance.resourceId,
    '--start-time', iso(window.start),
    '--end-time', iso(window.end),
    '--period-in-seconds', '3600',
    '--metric-queries', JSON.stringify([{ Metric: 'db.load.avg' }]),
  ], { ...ctx, region: instance.region }).catch(() => null);

  const points = (response?.MetricList?.[0]?.DataPoints ?? [])
    .map((point) => point.Value)
    .filter((value) => typeof value === 'number');

  return points.length ? points.reduce((a, b) => a + b, 0) / points.length : 0;
}

// ─────────────────────────────────────────────────────────────────────────────
// 응답 → 행 변환
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @typedef {object} Row
 * @property {number} rank
 * @property {number} load      해당 쿼리의 평균 활성 세션 수(AAS)
 * @property {number} sharePct  인스턴스 전체 부하 대비 비중
 * @property {{name: string, pct: number}[]} waits  이 쿼리가 겪은 대기 이벤트 분해
 * @property {string} sqlId
 * @property {string} statement
 */

const round = (value, digits) => {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
};

const normalizeSql = (text) => (text ?? '-').replace(/[\n\r\t]+/g, ' ').replace(/ {2,}/g, ' ').trim();

/**
 * PartitionKeys(대기 이벤트 이름 배열)와 각 Key의 Partitions(같은 순서의 값 배열)를
 * 짝지어 사람이 읽을 수 있는 형태로 만든다.
 * @returns {Row[]}
 */
function toRows(response, totalLoad) {
  const waitNames = (response.PartitionKeys ?? [])
    .map((partition) => partition.Dimensions?.['db.wait_event.name'] ?? '?');
  const keys = response.Keys ?? [];
  const keySum = keys.reduce((sum, key) => sum + (key.Total ?? 0), 0);
  const denominator = totalLoad > 0 ? totalLoad : keySum;

  return keys.map((key, index) => {
    const total = key.Total ?? 0;
    const partitions = key.Partitions ?? [];
    const waits = waitNames
      .map((name, i) => ({ name, pct: total > 0 ? round(((partitions[i] ?? 0) / total) * 100, 1) : 0 }))
      .filter((wait) => wait.pct >= 0.1);

    return {
      rank: index + 1,
      load: round(total, 3),
      sharePct: denominator > 0 ? round((total / denominator) * 100, 1) : 0,
      waits,
      sqlId: key.Dimensions?.['db.sql_tokenized.id'] ?? '-',
      statement: normalizeSql(key.Dimensions?.['db.sql_tokenized.statement']),
    };
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// 출력
// ─────────────────────────────────────────────────────────────────────────────

const ESC = String.fromCharCode(27);
const useColor = Boolean(process.stdout.isTTY) && !process.env.NO_COLOR;
const bold = (text) => (useColor ? `${ESC}[1m${text}${ESC}[0m` : text);
const dim = (text) => (useColor ? `${ESC}[2m${text}${ESC}[0m` : text);
const rule = (char, width = 78) => char.repeat(width);

/** 한글·전각 문자는 터미널에서 두 칸을 차지하므로, 표 정렬은 표시 폭 기준으로 맞춘다. */
const WIDE_CHAR = /[\u1100-\u115F\u2E80-\uA4CF\uAC00-\uD7A3\uF900-\uFAFF\uFE30-\uFE6F\uFF00-\uFF60\uFFE0-\uFFE6]/;
const displayWidth = (text) => [...text].reduce((sum, ch) => sum + (WIDE_CHAR.test(ch) ? 2 : 1), 0);
const padTo = (text, width) => text + ' '.repeat(Math.max(0, width - displayWidth(text)));
const padStartTo = (text, width) => ' '.repeat(Math.max(0, width - displayWidth(text))) + text;

/** 표시 폭 기준으로 자르고, 잘린 경우 말줄임표를 붙인다. */
function truncate(text, width) {
  if (displayWidth(text) <= width) return text;
  let result = '';
  let used = 0;
  for (const char of text) {
    const charWidth = WIDE_CHAR.test(char) ? 2 : 1;
    if (used + charWidth > width - 1) break;
    result += char;
    used += charWidth;
  }
  return `${result}…`;
}

const formatWaits = (waits) => (waits.length
  ? [...waits].sort((a, b) => b.pct - a.pct).map((wait) => `${wait.name} ${wait.pct}%`).join(' · ')
  : '-');

/** 컬럼 폭. 쿼리 컬럼이 세로로 맞으려면 앞쪽이 모두 고정폭이어야 한다. */
const COLUMNS = { rank: 4, load: 8, share: 8, gap: 2 };
const prefixWidth = (waitWidth) =>
  COLUMNS.rank + COLUMNS.load + COLUMNS.share + COLUMNS.gap + waitWidth + COLUMNS.gap + 2;

function renderInstance({ instance, rows, totalLoad, window }, { statementWidth, waitWidth }) {
  const lineWidth = Math.min(prefixWidth(waitWidth) + statementWidth, 120);

  console.log(
    `\n${bold(instance.name)}  —  ${instance.instanceClass} · ${instance.cluster} · ${instance.role}`
    + `  |  전체 DB Load 평균 ${round(totalLoad, 3)}`,
  );
  console.log(dim(rule('─', lineWidth)));

  if (rows.length === 0) {
    console.log(`  (이 구간에 상위 쿼리 데이터 없음 — ${iso(window.start)} ~ ${iso(window.end)}, period ${window.period}s)`);
    return;
  }

  console.log(dim(
    '  ' + padTo('#', COLUMNS.rank) + padStartTo('AAS', COLUMNS.load) + padStartTo('비중', COLUMNS.share)
    + '  ' + padTo('대기 이벤트', waitWidth) + '  ' + '쿼리',
  ));

  for (const row of rows) {
    console.log(
      '  ' + padTo(`#${row.rank}`, COLUMNS.rank)
      + padStartTo(String(row.load), COLUMNS.load)
      + padStartTo(`${row.sharePct}%`, COLUMNS.share)
      + '  ' + padTo(truncate(formatWaits(row.waits), waitWidth), waitWidth)
      + '  ' + truncate(row.statement, statementWidth),
    );
  }
}

function renderSummary(entries, { summaryTop, statementWidth, waitWidth }) {
  if (entries.length === 0) return;
  const nameWidth = Math.min(Math.max(...entries.map((e) => displayWidth(e.instance.name))), 34);
  const lineWidth = Math.min(COLUMNS.load + 2 + nameWidth + 2 + waitWidth + 2 + statementWidth, 120);

  console.log(`\n\n${bold(`전체 인스턴스 통합 — 부하 상위 ${summaryTop}개 쿼리`)}`);
  console.log(dim(rule('═', lineWidth)));
  console.log(dim(
    '  ' + padStartTo('AAS', COLUMNS.load) + '  ' + padTo('인스턴스', nameWidth)
    + '  ' + padTo('대기 이벤트', waitWidth) + '  ' + '쿼리',
  ));

  const ranked = [...entries].sort((a, b) => b.row.load - a.row.load).slice(0, summaryTop);
  for (const { instance, row } of ranked) {
    console.log(
      '  ' + padStartTo(String(row.load), COLUMNS.load)
      + '  ' + padTo(truncate(instance.name, nameWidth), nameWidth)
      + '  ' + padTo(truncate(formatWaits(row.waits), waitWidth), waitWidth)
      + '  ' + truncate(row.statement, statementWidth),
    );
  }
  console.log('');
}

function renderTsv(entries) {
  const header = ['region', 'instance', 'class', 'cluster', 'role', 'aas', 'share_pct', 'waits', 'sql_id', 'statement'];
  console.log(header.join('\t'));
  for (const { instance, row } of entries) {
    console.log([
      instance.region, instance.name, instance.instanceClass, instance.cluster, instance.role,
      row.load, row.sharePct, formatWaits(row.waits), row.sqlId, row.statement,
    ].join('\t'));
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 진단 모드 (--probe)
// ─────────────────────────────────────────────────────────────────────────────

/** 구간 길이·period 조합을 훑어 어디서부터 빈 응답이 오는지 표로 보여준다. */
async function runProbe(instance, ctx) {
  const combos = [
    { minutes: 180, period: 60 },
    { minutes: 720, period: 300 },
    { minutes: 1440, period: 300 },
    { minutes: 1440, period: 3600 },
    { minutes: 4320, period: 3600 },
    { minutes: 10010, period: 3600 },
    { minutes: 10075, period: 3600 },
    { minutes: 10075, period: 86400 },
    { minutes: 10075, period: undefined }, // PI 자동 선택
  ];
  const widths = [10, 8, 22, 22, 6];

  console.log(`\n${bold(instance.name)} (${instance.resourceId}) — 구간별 응답 확인\n`);
  console.log(['구간', 'period', '요청 StartTime', 'AlignedStartTime', 'Keys']
    .map((head, i) => padTo(head, widths[i])).join(''));
  console.log(dim(rule('─', 96)));

  const end = new Date();
  for (const { minutes, period } of combos) {
    const start = new Date(end.getTime() - minutes * MINUTE);
    const args = [
      'pi', 'describe-dimension-keys',
      '--service-type', 'RDS',
      '--identifier', instance.resourceId,
      '--start-time', iso(start),
      '--end-time', iso(end),
      '--metric', 'db.load.avg',
      '--group-by', JSON.stringify({ Group: 'db.sql_tokenized', Dimensions: ['db.sql_tokenized.id'], Limit: 5 }),
    ];
    if (period !== undefined) args.push('--period-in-seconds', String(period));

    const cells = [`${minutes}분`, String(period ?? 'auto'), iso(start)];
    try {
      const response = await awsJson(args, { ...ctx, region: instance.region });
      const keyCount = (response.Keys ?? []).length;
      cells.push(String(response.AlignedStartTime ?? '-').slice(0, 19), String(keyCount));
      console.log(cells.map((cell, i) => padTo(cell, widths[i])).join('') + (keyCount === 0 ? '  <-- 빈 응답' : ''));
    } catch (error) {
      cells.push('-', 'ERR');
      console.log(cells.map((cell, i) => padTo(cell, widths[i])).join('') + `  ${error.message.slice(0, 60)}`);
    }
  }
  console.log('');
}

// ─────────────────────────────────────────────────────────────────────────────
// 실행
// ─────────────────────────────────────────────────────────────────────────────

/** 동시 실행 개수를 제한하며 입력 순서를 보존한 결과 배열을 만든다. */
async function mapWithConcurrency(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;

  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      results[index] = await worker(items[index], index);
    }
  });

  await Promise.all(runners);
  return results;
}

async function resolveRegions(config, ctx) {
  if (config.regions.length > 0) return config.regions;
  try {
    const args = ['configure', 'get', 'region'];
    if (ctx.profile) args.push('--profile', ctx.profile);
    const { stdout } = await execFileAsync('aws', args);
    if (stdout.trim()) return [stdout.trim()];
  } catch {
    // aws configure get은 값이 없으면 exit 1을 반환한다. 아래에서 안내한다.
  }
  console.error('오류: 리전을 확인할 수 없습니다. --region 으로 지정하세요.');
  process.exit(2);
}

async function analyzeInstance(instance, config, ctx) {
  const window = buildWindow({
    days: config.days,
    retentionDays: instance.retentionDays,
    periodOverride: config.period,
  });

  try {
    const [response, totalLoad] = await Promise.all([
      fetchTopSql(instance, window, ctx, config),
      fetchTotalLoad(instance, window, ctx),
    ]);
    if (config.verbose) {
      console.error(`  [${instance.name}] start=${iso(window.start)} period=${window.period}s`
        + ` aligned=${response.AlignedStartTime ?? '-'} keys=${(response.Keys ?? []).length}`);
    }
    return { instance, window, totalLoad, rows: toRows(response, totalLoad) };
  } catch (error) {
    console.error(`  ${instance.name}: 조회 실패 — ${error.message}`);
    return null;
  }
}

async function main() {
  const config = parseCliArgs();
  const ctx = { profile: config.profile };
  const regions = await resolveRegions(config, ctx);

  if (config.output === 'table') {
    console.error(`조회 종료: ${iso(new Date())} / 요청 기간: 최근 ${config.days}일 / 리전: ${regions.join(', ')}`);
  }

  /** @type {{instance: Instance, row: Row}[]} */
  const flatRows = [];
  const jsonOutput = [];

  for (const region of regions) {
    let instances;
    try {
      instances = await listInstances(region, ctx, config.nameFilter);
    } catch (error) {
      console.error(`  ${region}: 인스턴스 목록 조회 실패 — ${error.message}`);
      continue;
    }
    if (instances.length === 0) {
      console.error(`  ${region}: Performance Insights가 켜진 available 인스턴스가 없습니다.`);
      continue;
    }
    if (config.output === 'table') console.log(`\n${bold(`### 리전: ${region} (${instances.length}개)`)}`);

    if (config.probe) {
      for (const instance of instances) await runProbe(instance, ctx);
      continue;
    }

    const analyzed = await mapWithConcurrency(instances, config.concurrency,
      (instance) => analyzeInstance(instance, config, ctx));

    for (const result of analyzed) {
      if (!result) continue;
      if (config.quiet && result.rows.length === 0) continue;

      if (config.output === 'table') renderInstance(result, config);
      if (config.output === 'json') {
        jsonOutput.push({
          region,
          instance: result.instance.name,
          instanceClass: result.instance.instanceClass,
          cluster: result.instance.cluster,
          role: result.instance.role,
          startTime: iso(result.window.start),
          endTime: iso(result.window.end),
          periodInSeconds: result.window.period,
          totalLoad: round(result.totalLoad, 3),
          rows: result.rows,
        });
      }
      for (const row of result.rows) flatRows.push({ instance: result.instance, row });
    }
  }

  if (config.probe) return;
  if (config.output === 'table') renderSummary(flatRows, config);
  if (config.output === 'tsv') renderTsv(flatRows);
  if (config.output === 'json') console.log(JSON.stringify(jsonOutput, null, 2));
}

main().catch((error) => {
  console.error(`예기치 못한 오류: ${error.stack ?? error.message}`);
  process.exit(1);
});
