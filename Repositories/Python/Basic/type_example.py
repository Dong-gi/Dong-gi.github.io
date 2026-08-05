print(int(123)) # 123
print(int('123')) # 123
print(int(1.23)) # 1
# print(int('1.23')) # ValueError: invalid literal for int() with base 10: '1.23'
print(int(True)) # 1
print(int(False)) # 0
print(int('0xFF', 16)) # 255
print(int('100', 15)) # 225
# print(int('100', 100)) # ValueError: int() base must be >= 2 and <= 36, or 0


print(divmod(2.8, 0.5)) # (5.0, 0.2999999999999998)
print('{:.20f}'.format(1.7777777777777777777777777777777)) # 1.77777777777777767909
print(float('1.234')) # 1.234
print(float('0.12e4')) # 1200.0
print(float(True)) # 1.0
print(float(False)) # 0.0


print('Hello' + " World" + '"!"') # Hello World"!"
print('Hello' " World" '"!"') # Hello World"!"
print('Hello' + ' World' * 4) # Hello World World World World
print("""Hello
World!!""")
print(r"""Hello
World!!""")

print('Hello World!'[::-1]) # !dlroW olleH
print('Hello World!'[3]) # l
print('Hello World!'[3 : 8]) # lo Wo
print('Hello World!'[3:]) # lo World!
print('Hello World!'[:8]) # Hello Wo
print('Hello World!'[0 : 8 : 2]) # HloW

print(len('Hello World!')) # 12

print('Hello World!'.split()) # ['Hello', 'World!']
print('Hello World!'.split('l')) # ['He', '', 'o Wor', 'd!']
print('Hello World!'.split('ll')) # ['He', 'o World!']
print('?'.join('Hello World!'.split('o'))) # Hell? W?rld!

print('  Hello World!  '.strip()) # Hello World!
print('Hello World!'.strip('H')) # ello World!

print('Hello World!'.center(30))
#         Hello World!
print('Hello World!'.ljust(30))
#Hello World!
print('Hello World!'.rjust(30))
#                  Hello World!

print('Hello World!'.replace('l', '?')) # He??o Wor?d! # 정규표현식 이용 가능
print('Hello World!'.replace('l', '?', 2)) # He??o World! # 바꿀 횟수


print([1, 2, 3, 4, 5]) # [1, 2, 3, 4, 5]
print(list('Hello World!')) # ['H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd', '!']

list1 = list('Hello World!')
list1[3::2] = range(5) # 할당문의 대상이 슬라이스인 경우, 우측 피연산자는 반복 가능한 객체여야 한다.
print(list1[-1], list1[::-1]) # 4 [4, 'd', 3, 'r', 2, 'W', 1, 'o', 0, 'l', 'e', 'H']

list1 = list('Hello ')
list1 += ('!@#$%')
list1.extend(('abcde'))
print(list1) # ['H', 'e', 'l', 'l', 'o', ' ', '!', '@', '#', '$', '%', 'a', 'b', 'c', 'd', 'e']

list1 = list('abcabcabc')
list1.append('=')
print(list1) # ['a', 'b', 'c', 'a', 'b', 'c', 'a', 'b', 'c', '=']
list1.insert(3, '3')
print(list1) # ['a', 'b', 'c', '3', 'a', 'b', 'c', 'a', 'b', 'c', '=']
list1.remove('b')
print(list1) # ['a', 'c', '3', 'a', 'b', 'c', 'a', 'b', 'c', '=']
del list1[0]
print(list1) # ['c', '3', 'a', 'b', 'c', 'a', 'b', 'c', '=']
del list1[-1]
print(list1) # ['c', '3', 'a', 'b', 'c', 'a', 'b', 'c']
list1.pop()
print(list1) # ['c', '3', 'a', 'b', 'c', 'a', 'b']

list1 = list('abcabcabc')
print(list1.index('b')) # 1 # index() : 위치 확인
print('c' in list1) # True # in : 포함 여부 확인
print(list1.count('a') ) # 3 # count() : 요소 개수 확인

list1 = list('abcabcabc')
list1.sort()
print(list1) # ['a', 'a', 'a', 'b', 'b', 'b', 'c', 'c', 'c']
list1.sort(reverse=True)
print(list1) # ['c', 'c', 'c', 'b', 'b', 'b', 'a', 'a', 'a']

list1 = list('abcabcabc')
list2 = list1.copy() # 깊은 복사
list3 = list(list1) # 깊은 복사
list4 = list1[::] # 깊은 복사
list1[0] = '>>'
print(list1) # ['>>', 'b', 'c', 'a', 'b', 'c', 'a', 'b', 'c']
print(list2) # ['a', 'b', 'c', 'a', 'b', 'c', 'a', 'b', 'c']
print(list3) # ['a', 'b', 'c', 'a', 'b', 'c', 'a', 'b', 'c']
print(list4) # ['a', 'b', 'c', 'a', 'b', 'c', 'a', 'b', 'c']

# 유의
a = [1, 2, 3]
b = ['a', 'b', 'c']
a[1:2] = b
b[1] = 111
print(a) # [1, 'a', 'b', 'c', 3]
print(b) # ['a', 111, 'c']


print((1, 2, 3, 'abcd')) # (1, 2, 3, 'abcd')
tuple1 = tuple('h' * 3)
tuple1 += (4, ) # 하나일 땐 뒤에 콤마를 붙여야 튜플로 인식한다.
tuple1 += (5, 6)
print(tuple1) # ('h', 'h', 'h', 4, 5, 6)

tuple1 = (1, 2, 3, 4, 5, 6)
_, _, _, a, b, c = tuple1
c, b, a = a, b, c
print(a, b, c) # 6 5 4


print({1, 2, 3, 4, 5}) # {1, 2, 3, 4, 5}
print(set('12345')) # {'3', '5', '4', '1', '2'} # 집합은 순서와 무관

s = set({1, 2, 3})
s.add(5)
print(s) # {1, 2, 3, 5}

print(set({'a' : 'b', 'c' : 'd'})) # {'a', 'c'} # 딕셔너리의 키만 사용

menus = {
    'menu1' : {'재료1', '재료3'},
    'menu2' : {'재료2', '재료3', '재료4'},
    'menu3' : {'재료1', '재료3', '재료5'},
    'menu4' : {'재료4', '재료5', '재료6'}
}
for menu, ingredients in menus.items():
    if not '재료1' in ingredients:
        print(menu)
# menu2
# menu4

menus = {
    'menu1' : {'재료1', '재료3'},
    'menu2' : {'재료2', '재료3', '재료4'},
    'menu3' : {'재료1', '재료3', '재료5'},
    'menu4' : {'재료4', '재료5', '재료6'}
}
print(menus['menu1'] & menus['menu2']) # {'재료3'} # 교집합
print(menus['menu1'] | menus['menu2']) # {'재료1', '재료3', '재료2', '재료4'} # 합집합
print(menus['menu1'] - menus['menu2']) # {'재료1'} # 차집합
print(menus['menu1'] ^ menus['menu2']) # {'재료1', '재료2', '재료4'} # 대칭차집합

# 부분집합 판정
print({1, 2, 3} < {1, 2, 3}) # False # 진부분집합
print({1, 2, 3} <= {1, 2, 3}) # True # >, >=도 가능


print({'key' : 'value', 123 : 456}) # {'key': 'value', 123: 456}

dic = dict()
dic['key1'] = 'val1'
print('key1' in dic)
dic[12] = 34
print(dic) # {'key1': 'val1', 12: 34}

dic.update({'key1': 'val3', 'key2': 'val4'})
print(dic) # {'key1': 'val3', 12: 34, 'key2': 'val4'}
del dic['key1']
print(dic) # {12: 34, 'key2': 'val4'}
dic.clear()
print(dic) # {}

print(dict([['a', 'b'], ('c', 'd')])) # {'a': 'b', 'c': 'd'}
print(dict(('ab', 'cd'))) # {'a': 'b', 'c': 'd'}
# dict(['a', 'b', 'c', 'd']) # ValueError: dictionary update sequence element #0 has length 1; 2 is required

dic = dict(('ab', 'cd'))
print(dic.keys()) # dict_keys(['a', 'c'])
print(dic.values()) # dict_values(['b', 'd'])
print(dic.items()) # dict_items([('a', 'b'), ('c', 'd')])

dic1 = dict(('ab', 'cd'))
dic2 = dic1.copy() # 깊은 복사
dic1['a'] = 'z'
print(dic1) # {'a': 'z', 'c': 'd'}
print(dic2) # {'a': 'b', 'c': 'd'}

a, b, *c = range(5)
print(a, b, c) # 0 1 [2, 3, 4]
a, *b, c = range(5)
print(a, b, c) # 0 [1, 2, 3] 4
*a, b, c = range(5)
print(a, b, c) # [0, 1, 2] 3 4
a, (b, c) = (1, (2, 3))
print(a, b, c) # 1 2 3