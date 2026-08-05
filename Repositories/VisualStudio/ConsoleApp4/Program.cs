var point2D1 = new Point2D { X = 1, Y = 2 };
var point3D1 = new Point3D { X = 3, Y = 4, Z = 5 };

Point2D point2D2 = (Point2D)point3D1;
Point3D point3D2 = point2D1;
Console.WriteLine(point2D2);
Console.WriteLine(point3D2);

class Point2D
{
    public int X { get; set; }
    public int Y { get; set; }

    public override string ToString()
    {
        return System.Text.Json.JsonSerializer.Serialize(this);
    }

    public static implicit operator Point3D(Point2D p)
    {
        return new Point3D { X = p.X, Y = p.Y };
    }
}

class Point3D
{
    public int X { get; set; }
    public int Y { get; set; }
    public int Z { get; set; }

    public override string ToString()
    {
        return System.Text.Json.JsonSerializer.Serialize(this);
    }

    public static explicit operator Point2D(Point3D p)
    {
        return new Point2D { X = p.X, Y = p.Y };
    }
}
