import random

l = []
for _ in range(1, 1000):
    l.append(random.randint(0, 100))
size = len(str(max(l)))
radixList = [[] for _ in range(10)]
for radix in range(size):
    for x in l:
        radixList[(x // (10 ** radix)) % 10].append(x)
    l = [n for subList in radixList for n in subList]
    radixList = [[] for _ in range(10)]

print(l)