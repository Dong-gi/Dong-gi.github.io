i = 1;
while i < 10:
    print(i)
    i += 2
    if(i%2 == 0):
        print('found an even number')
        break
else:
    print('no even number')


for i in range(1, 5):
    if(i == 11):
        break
    print(i)
else:
    print("didn't meet 11")