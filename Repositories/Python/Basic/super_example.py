class A():
    def m(self):
        print('a')

class B(A):
    def m(self):
        print('b')

class C(B):
    def m(self):
        print('c')
        
class D(C):
    def m(self):
        super().m()
        super(D, D()).m()
        super(C, D()).m()
        super(B, D()).m()

D().m()
'''
c
c
b
a
'''