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
