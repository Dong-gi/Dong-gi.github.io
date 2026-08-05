msg = 'World'
print(f"Hello {msg}!") # Hello World!

class Test:
    def __str__(self):
        return '테스트.__str__'
    def __repr__(self):
        return '테스트.__repr__'
print(f"{Test()!s}")    # 테스트.__str__
print(f"{Test()!r}")    # 테스트.__repr__
print(f"{Test()!a}")    # \ud14c\uc2a4\ud2b8.__repr__

num = 1234.56789
print(f"{num:<20f}")    # 1234.567890
print(f"{num:>20f}")    #          1234.567890
print(f"{num:=+20f}")   # +        1234.567890
print(f"{num:^20f}")    #     1234.567890