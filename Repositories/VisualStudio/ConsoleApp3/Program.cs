class Point2D
{
    public int x { get; set; }
    public int y { get; set; }

    public static Point2D operator +(Point2D p1, Point2D p2)
    {
        return new Point2D { x = p1.x + p2.x, y = p1.y + p2.y };
    }

    public override string ToString()
    {
        return System.Text.Json.JsonSerializer.Serialize(this);
    }
}

public class Program
{
    public static void Main(string[] args)
    {
        Console.WriteLine(new Point2D { x = 1, y = 2 } + new Point2D { x = 3, y = 4 });
    }
}