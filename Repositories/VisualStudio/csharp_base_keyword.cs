public class Person
{
    public virtual void GetInfo() { }
}
class Employee : Person
{
    public override void GetInfo()
    {
        base.GetInfo();
    }
}


public class BaseClass
{
    public BaseClass() { }
}
public class DerivedClass : BaseClass
{
    public DerivedClass() : base() { }
}
