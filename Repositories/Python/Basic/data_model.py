import numpy as np
import numbers







arr = np.array([
    [[ 1, 2, 3], [ 4, 5, 6], [ 7, 8, 9]],
    [[10,11,12], [13,14,15], [16,17,18]],
    [[19,20,21], [22,23,24], [25,26,27]],
])

print(arr)
print(arr[:, :, :])
print(arr[...])
'''
[[[ 1  2  3]
  [ 4  5  6]
  [ 7  8  9]]

 [[10 11 12]
  [13 14 15]
  [16 17 18]]

 [[19 20 21]
  [22 23 24]
  [25 26 27]]]
'''

print(arr[:1, :, :])
print(arr[:1, ...])
'''
[[[1 2 3]
  [4 5 6]
  [7 8 9]]]
'''


print(isinstance(True, numbers.Number)) # True
print(isinstance(True, numbers.Integral)) # True
print(isinstance(True, int)) # True
print(isinstance(True, bool)) # True
print((True + True) * (True + True)) # 4
print(False == 0) # True


t = ()
print(type(t)) # <class 'tuple'>
t = 1,
print(type(t)) # <class 'tuple'>
t = 1, 2
print(type(t)) # <class 'tuple'>


s = slice(10, 0, -2)
l = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
print(s.indices(0)) # (-1, -1, -2)
print(s.indices(5)) # (4, 0, -2)
print(s.indices(10)) # (9, 0, -2)
print(l[s]) # [10, 8, 6, 4, 2]
print(l[9 : 0 : -2]) # [10, 8, 6, 4, 2]
print(s.indices(15)) # (10, 0, -2)


def simple_coroutine():
    print('코루틴 시작')
    x = yield
    print('받은 값 ', x)
    return '코루틴 결과'

try:
    coroutine = simple_coroutine()
    print(type(coroutine))
    next(coroutine) # coroutine.send(None) 호출로 같은 효과를 볼 수 있다.
    # 코루틴 시작
    coroutine.send(111) # 받은 값  111
except StopIteration as e:
    print(e.value) # 코루틴 결과

def averager():
    total = 0.0
    count = 0
    average = 0.0
    while True:
        term = yield average
        total += term
        count += 1
        average = total/count

avg = averager()
print(avg.send(None)) # 0.0
print(avg.send(1))    # 1.0
print(avg.send(5))    # 3.0
print(avg.send(10))   # 5.333333333333333
avg.close()


from functools import wraps
def readyCoroutine(func):
    '''함수 데커레이터: func를 기동해서 첫 번째 yield까지 진행한다.'''
    @wraps(func)
    def proceed(*args, **kwargs):
        gen = func(*args, **kwargs)
        next(gen)
        return gen
    return proceed

@readyCoroutine
def new_averager():
    total = 0.0
    count = 0
    average = 0.0
    while True:
        term = yield average
        total += term
        count += 1
        average = total/count

avg = new_averager()
print(avg.send(1))    # 1.0
print(avg.send(5))    # 3.0
print(avg.send(10))   # 5.333333333333333
avg.close()

def same_as_range(n):
    _ = yield from range(n)
    print('End', _)
    return _

for num in same_as_range(10):
    print(num, end=',') # 0,1,2,3,4,5,6,7,8,9,End None


from collections import namedtuple

Result = namedtuple('Result', 'count average')

def average():
    total = 0.0
    count = 0
    while True:
        value = yield
        if value is None:
            break
        total += value
        count += 1
    return Result(count, total / count)

@readyCoroutine
def grouper(results, key):
    while True:
        results[key] = yield from average()
        # 매 반복마다 average 객체가 생성되어 코루틴으로 작동한다.

data = {
    'weight(kg)': [1, 2, 3, 4, 5],
    'height(cm)': [10, 20, 30, 40, 50]
}

results = {}
for key, values in data.items():
    group = grouper(results, key)
    for value in values:
        group.send(value)
        # value는 subgenerator에 곧바로 전달되며, delegating generator는 값을 볼 수 없다.
        # None이라면 하위 제너레이터의 __next__()가 호출되고, None이 아니면 send(value) 그대로 호출된다.
        # 하위 제너레이터에서 return expr 문을 실행하면 StopIteration(expr) 예외가 발생한다.
        # 하위 제너레이터에서 StopIteration 예외가 발생하면 delegating generator의 실행이 재개된다. 그 외의 예외는 delegating generator에 전달된다.
        # 하위 제너레이터가 실행을 마친 후 발생한 StopIteration 예외의 첫 번째 인수가 yield from 표현식의 값이 된다.
    group.send(None)

print(results) # {'weight(kg)': Result(count=5, average=3.0), 'height(cm)': Result(count=5, average=30.0)}

def infinite_series(start):
    num = start
    def yield_as_is(_):
        yield _
    while True:
        num += 1
        yield from yield_as_is(num)

for n in infinite_series(-10):
    print(n, end=',') # -9,-8,-7,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7,8,9,10,11,
    if n > 10:
        break

import collections.abc
# collections.abc에서 tuple을 비롯한 내장 자료형이 Sequence의 가상 서브클래스로 등록되어 있다.
print(isinstance((1, 2, 3), collections.abc.Sequence)) # True

class NotSequence:
    pass
collections.abc.Sequence.register(NotSequence)
print(isinstance(NotSequence(), collections.abc.Sequence)) # True

class HasLength:
    def __len__(self):
        return 1
print(isinstance(HasLength(), collections.abc.Sized)) # True
# abc.Sized가 __subclasshook__()라는 특별 클래스 메서드에서 __len__() 메서드를 발견하면 가상 서브클래스로 간주하기 때문
'''
class Sized(metaclass=ABCMeta):
    @classmethod
    def __subclasshook__(cls, C):
        if cls is Sized:
            return _check_methods(C, "__len__")
        return NotImplemented
'''