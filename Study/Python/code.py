'''
code0001.py
파이썬 자료형1
'''

print('자료형 : 숫자 - 정수')

# 정수는 0으로 시작할 수 없다.
# print(0123456789)

print(1/3) # /의 결과는 부동소수
print(4//3) # //의 결과는 정수 몫
print(divmod(7, 3)) # 몫과 나머지 동시에 얻기

print(0b1111) # 2진수
print(0o17) # 8진수
print(0xf) # 16진수

print('"123" -> {0}'.format(int('123'))) # 형 변환.
print('True -> {0}'.format(int(True))) # 1
print('False -> {0}'.format(int(False))) # 0
print('1.23 -> {0}'.format(int(1.23)))
#print('"1.23" -> {0}'.format(int('1.23'))) # 소수점, 지수 포함 문자열 불가능.
print(int('0xf', 16)) # 진법 입력 가능
print(int('77', 15))

print(2**4) # 거듭제곱 **
print(10**100) # 파이썬 3부터 단일 정수 타입(int)을 사용하며, 크기 제한이 사라졌다


print('자료형 : 숫자 - 부동소수')
# +, -, *, /, //, **, %, divmod() 모두 가능
# 형 변환 : float()
print('{0:.20f}'.format(1.77777777777777777777777)) # float 정밀도는 그대로


print('자료형 : 문자열')
# 문자열은 불변한다.
print('Hello' + " World" + '"!"') # '', ""모두 가능
print('''Hello
World!''') # 여러 줄 '''~''', """~"""
# 형 변환 : str()
# 문자열 결합 연산자 : +
print('Hello '*3) # 문자열 복제
print('Hello'[1]) # 추출
# print('Hello'[7]) # 범위 오류 0~4
# 슬라이스 [start : end : step], end 자체는 미포함
STR = 'Hello World!'
print(STR[:])
print(STR[3:])
print(STR[3:8])
print(STR[:8:2])
print(STR[8:3:-2])
print(STR[::-1])

print(len(STR)) # 길이 반환
print(STR.split()) # 공백 문자로 분리
print(STR.split('l')) # 리스트로 반환
print('*'.join(STR.split('l'))) # 문자열로 리스트 각 원소 접합
print(STR.strip('H')) # 앞뒤 문자열 제거. 기본은 공백 제거
''' endswith(), startswith(), find(), rfind(), count(), isalnum()
    strip(), capitalize(), title(), upper(), lower(), swapcase() '''
print(STR.center(30))
print(STR.ljust(30))
print(STR.rjust(30))

print(STR.replace('l', '*')) # 정규표현식 이용 가능
print(STR.replace('l', '*', 2)) # 바꿀 횟수
