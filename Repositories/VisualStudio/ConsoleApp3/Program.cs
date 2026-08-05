Console.WriteLine(new Point2D { X = 1, Y = 2 } + new Point2D { X = 3, Y = 4 });

class Point2D
{
    public int X { get; set; }
    public int Y { get; set; }

    public static Point2D operator +(Point2D p1, Point2D p2)
    {
        return new Point2D { X = p1.X + p2.X, Y = p1.Y + p2.Y };
    }

    public override string ToString()
    {
        return System.Text.Json.JsonSerializer.Serialize(this);
    }
}
