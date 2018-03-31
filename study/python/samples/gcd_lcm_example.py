def gcd_lcm(a, b):
    if a <= 0 or b <= 0:
        raise ValueError("a, b both most > 0")
    if b > a:
        a, b = b, a
    m = a * b
    while b > 0:
        a, b = b, a % b
    return a, m // a

print(gcd_lcm(30, 50))