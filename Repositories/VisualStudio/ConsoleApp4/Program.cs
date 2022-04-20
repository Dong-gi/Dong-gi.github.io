class Point2D
{
    public int x { get; set; }
    public int y { get; set; }

    public override string ToString()
    {
        return System.Text.Json.JsonSerializer.Serialize(this);
    }

    public static implicit operator Point3D(Point2D p)
    {
        return new Point3D { x = p.x, y = p.y };
    }
}

class Point3D
{
    public int x { get; set; }
    public int y { get; set; }
    public int z { get; set; }

    public override string ToString()
    {
        return System.Text.Json.JsonSerializer.Serialize(this);
    }

    public static explicit operator Point2D(Point3D p)
    {
        return new Point2D { x = p.x, y = p.y };
    }
}

public class Program
{
    public static void Main(string[] args)
    {
        var point2D1 = new Point2D { x = 1, y = 2 };
        var point3D1 = new Point3D { x = 3, y = 4, z = 5 };

        Point2D point2D2 = (Point2D)point3D1;
        Point3D point3D2 = point2D1;
        Console.WriteLine(point2D2);
        Console.WriteLine(point3D2);
    }
}