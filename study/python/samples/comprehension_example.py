for i in zip((1, 2, 3), (4, 5, 6, 7)):
    print(i)

# List Comprehension
print('even nums : ' + str([2*n for n in range(1, 10)]))
print('even nums : ' + str([n for n in range(1, 20) if n%2 == 0]))

print('(x, y) : ' + str([(x, 2*y) for x in range(1, 3) for y in range(1, 4) if x != y]))
#print('(x, y) : ' + str([x, 2*y for x in range(1, 3) for y in range(1, 4) if x != y])) 튜플은 괄호를 생략할 수 없다.
print('(x, y) : ' + str([[(x, 2*y) for x in range(1, 3)] for y in range(1, 4)]))

print('flatten a list : ' + str([n for l in [[1, 2], [3, 4, 5]] for n in l]))

# Dictionary Comprehension
string = "qoiwnefoqi wenfoiq woenif oina3W2098RN2089NRQ0983"
print({key: string.count(key) for key in set(string)})

# Set Comprehension
print({key for key in string})

# Generator Comprehension
# 이터레이터에 데이터를 제공하는 한 방법. 데이터를 하나씩 소모하며, 저장하지 않아 재사용할 수 없다.
print((key for key in string))
for k in (key for key in string):
    print(k, end=' ')
