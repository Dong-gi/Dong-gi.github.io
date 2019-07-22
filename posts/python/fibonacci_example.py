def fibo(limit):
    a, b = 0, 1
    while b < limit:
        print(b, end=', ')
        a, b = b, a+b

if __name__ == "__main__":
    import sys
    fibo(int(sys.argv[1]))