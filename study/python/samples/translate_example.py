words1 = ('apple', 'egg')
words2 = ('사과', '달걀')

trans = str.maketrans(words1, words2)
s = '사과 1개 500원, 달걀 1판 3000원'
print(s.translate(trans))