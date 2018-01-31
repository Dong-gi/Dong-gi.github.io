use /*db_name*/;

create table test1
(
	id int unsigned not null auto_increment primary key,
	name char(20) not null
);

create table test2
(
	id1 int not null,
	id2 int not null,
	val1 float(6, 3),
	val2 date,
	val3 text,
	primary key(id1, id2)
);

# www.mysql.com 참고
/* TINYINT, BIT, BOOL, SMALLINT, MEDIUMINT, INT, BIGINT
 * FLOAT, DOUBLE, DECIMAL
 * DATE, TIME, DATETIME, TIMESTAMP, YEAR
 * CHAR(M) [BINARY|ASCII|UNICODE] : 길이 M(0~255)의 문자열, CHAR, VARCHAR(M) [BINARY]
 *      BINARY : 대소문자 구별 안 함
 * TINYBLOB : 255, TIBYTEXT, BLOB : 2^16-1, TEXT, MEDIUMBLOB : 2^24-1, MEDIUMTEXT, LONGBLOB : 2^32-1, LONOGTEXT
 * ENUM('value1', 'value2', ...) : 최대 65535개, SET('value1', 'value2', ...) : 최대 64개
 */
 
 insert into test1 values (null, 'Donggi');
 insert into test1 (name) values
	('a'),
	('b');
	
/*
select [options] items [into file_details] from tables
	[where conditions] [group by group_type] [having where_definition]
	[order by order_type] [limit limit_criteria]
	[procedure proc_name(arguments)] [lock_options];
*/

select name from test1;
select * from test1;
select name from test1 where id=1;
# where =, >, <, >=, <=, !=, <>, IS NOT NULL, IS NULL, BETWEEN, IN, NOT IN, LIKE, NOT LIKE, REGEXP
select name from test1 where name like '%m%';
# % : 여러 문자, _ : 한 글자
# REGEXP : POSIX 정규 표현식
select name, val3 from test1, test2 where test1.id = test2.id1 and val3 is not null;
select name, val3 from test1 left join test2 on test1.id = test2.id1;
/* Cartesian product(Full Join, Cross Join) : 모든 행의 조합. 테이블은 ','으로 구분하고 where는 쓰지 않는다.
 * Inner Join : full join + where절
 * Equi-Join
 * Left join(Right Join) : 한쪽 테이블에 값이 없어도 null로 된 tuple 표시
 */
select name, val3 from test1 as a, test2 as b where a.id = b.id1;
select * from test1 order by name;
select * from test1 order by name desc;

/* AVG(column) : 평균
 * COUNT(column) : NULL이 아닌 항목 개수
 * MIN(column) : 최소값
 * MAX(column) : 최대값
 * STD(column) : 표준편차
 * SUM(column) : 합
 */
select count(*) from test1;
select id1, avg(val1) from test2 group by id1;
# group by : id1이 같은 것끼리 그룹을 만든다.
select id1, avg(val1) from test2 group by id1 having avg(val1) > 10;

select name from test1 limit 0, 10;
# limit : i행부터 j개 행을 반환.

# 하위 쿼리
select id1, val1 from test2 where val1 = (select max(val1) from test2);

update test1 set name='ASDF' where id=1;

alter table test1 add val1 float(6, 3) after id;
alter table test1 drop val1;
drop table test1;
drop database db_name;

show tables;
show databases;
show tables from db_name;

/* 백업 : 백업 동안 업데이트를 막아야 한다.
 * lock tables table_name lock_type [, table_name lock_type ... ]
 * 		lock_type : READ, WRITE
 * mysqldump --opt --all-db_name > name.sql