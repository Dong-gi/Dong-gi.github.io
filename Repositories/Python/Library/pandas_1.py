import datetime
import pandas as pd
import numpy as np

# Series 생성
series = pd.Series([1, 3, 5, None, 7, 9])
print(series)
'''
0    1.0
1    3.0
2    5.0
3    NaN
4    7.0
5    9.0
dtype: float64
'''

# Series 연산
print(series + 1)
'''
0     2.0
1     4.0
2     6.0
3     NaN
4     8.0
5    10.0
dtype: float64
'''

# Series 연산
print(series * series)
'''
0     1.0
1     9.0
2    25.0
3     NaN
4    49.0
5    81.0
dtype: float64
'''

# DataFrame 생성
frame = pd.DataFrame(
    data = [[1, 2, 3], [4, 5, 6], [7, 8, 9]],
    index = ['one', 'two', 'three'],
    columns = ['col1', 'col2', 'col3'])
print(frame)
'''
       col1  col2  col3
one       1     2     3
two       4     5     6
three     7     8     9
'''

# DataFrame 생성
frame = pd.DataFrame({
    'name': ['name1', 'name2', 'name3'],
    'hp': [123, 456, 789],
    'release_date': [datetime.datetime.now()] * 3
})
print(frame)
'''
    name   hp               release_date
0  name1  123 2020-02-29 05:20:23.592426
1  name2  456 2020-02-29 05:20:23.592426
2  name3  789 2020-02-29 05:20:23.592426
'''

# DataFrame.index : 지정하지 않으면 행 순서대로 0, 1, 2, ...
print(frame.index)
'''
RangeIndex(start=0, stop=3, step=1)
'''

frame2 = frame.set_index('name') # ↔ reset_index
print(frame2)
'''
        hp               release_date
name
name1  123 2020-02-29 06:26:48.960401
name2  456 2020-02-29 06:26:48.960401
name3  789 2020-02-29 06:26:48.960401
'''

print(frame2.index)
'''
Index(['name1', 'name2', 'name3'], dtype='object', name='name')
'''

# DataFrame.columns
print(frame.columns)
'''
Index(['name', 'hp', 'release_date'], dtype='object')
'''

# DataFrame.dtypes
print(frame.dtypes)
'''
name                    object
hp                       int64
release_date    datetime64[ns]
dtype: object
'''

# Series 추출
print(frame['name'])
'''
0    name1
1    name2
2    name3
Name: name, dtype: object
'''

# 행 추출
print(frame[1:3])
'''
    name   hp               release_date
1  name2  456 2020-02-29 05:57:15.167504
2  name3  789 2020-02-29 05:57:15.167504
'''

# 행 추출 - loc
print(frame.loc[2])
'''
name                                 name3
hp                                     789
release_date    2020-02-29 06:04:38.661534
Name: 2, dtype: object
'''

# 슬라이싱 - loc
print(frame.loc[:, ['hp', 'name']])
'''
    hp   name
0  123  name1
1  456  name2
2  789  name3
'''

# 행 추출 - iloc
print(frame.iloc[2])
'''
name                                 name3
hp                                     789
release_date    2020-02-29 06:11:20.079638
Name: 2, dtype: object
'''

# 슬라이싱 - iloc
print(frame.iloc[0:2,1:3])
'''
    hp               release_date
0  123 2020-02-29 06:12:14.030536
1  456 2020-02-29 06:12:14.030536
'''

# 행 추출 - Boolean indexing
print(frame[pd.Series([True, False, True])])
'''
    name   hp               release_date
0  name1  123 2020-02-29 06:16:51.150870
2  name3  789 2020-02-29 06:16:51.150870
'''

# 행 추출 - Boolean indexing
print(frame[frame.hp > 300])
'''
    name   hp               release_date
1  name2  456 2020-02-29 06:19:34.181469
2  name3  789 2020-02-29 06:19:34.181469
'''

# 값 추출 - at
print(frame.at[1, 'hp'])
'''
456
'''

# 값 추출 - iat
print(frame.iat[1, 1])
'''
456
'''

# Series 추가
frame['defence'] = frame['hp'] // 2
frame['init_score'] = frame.hp + frame['defence'] # 일부러 속성 접근 2가지를 혼용함
frame['released'] = (frame['release_date'] <= datetime.datetime.now())
print(frame)
'''
    name   hp               release_date  defence  init_score  released
0  name1  123 2020-02-29 05:57:53.877089       61         184      True
1  name2  456 2020-02-29 05:57:53.877089      228         684      True
2  name3  789 2020-02-29 05:57:53.877089      394        1183      True
'''

# DataFrame.describe() : 각 Series별 요약 정보
print(frame.describe())
'''
          hp     defence   init_score
count    3.0    3.000000     3.000000
mean   456.0  227.666667   683.666667
std    333.0  166.500250   499.500083
min    123.0   61.000000   184.000000
25%    289.5  144.500000   434.000000
50%    456.0  228.000000   684.000000
75%    622.5  311.000000   933.500000
max    789.0  394.000000  1183.000000
'''

# DataFrame.T : 전치
print(frame.T)
'''
                                       0  ...                           2
name                               name1  ...                       name3
hp                                   123  ...                         789
release_date  2020-02-29 06:00:48.188757  ...  2020-02-29 06:00:48.188757
defence                               61  ...                         394
init_score                           184  ...                        1183
released                            True  ...                        True

[6 rows x 3 columns]
'''

# DataFrame.sort_index : 열 순서 변경
print(frame.sort_index(axis=1))
'''
   defence   hp  init_score   name               release_date  released
0       61  123         184  name1 2020-02-29 06:00:48.188757      True
1      228  456         684  name2 2020-02-29 06:00:48.188757      True
2      394  789        1183  name3 2020-02-29 06:00:48.188757      True
'''

# DataFrame.sort_values : 행 순서 변경
print(frame.sort_values(by='hp', ascending=False))
'''
    name   hp               release_date  defence  init_score  released
2  name3  789 2020-02-29 06:00:48.188757      394        1183      True
1  name2  456 2020-02-29 06:00:48.188757      228         684      True
0  name1  123 2020-02-29 06:00:48.188757       61         184      True
'''

# dropna() : 값이 없는 행 지우기
# fillna() : 기본값 채워주기
print(pd.isna(frame))
'''
    name     hp  release_date  defence  init_score  released
0  False  False         False    False       False     False
1  False  False         False    False       False     False
2  False  False         False    False       False     False
'''