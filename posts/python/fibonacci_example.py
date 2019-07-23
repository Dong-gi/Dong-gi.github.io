def fibo(limit):
    a, b = 0, 1
    while b < limit:
        print(b, end=', ')
        a, b = b, a+b

if __name__ == "__main__":
    import sys
    print(sys.argv)
    fibo(int(sys.argv[1]))

# 명령행에서 아래와 같이 실행
# python fibonacci_example.py 100