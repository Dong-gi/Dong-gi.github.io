public static void AlignmentExample() {
    // 가운데 정렬은 없음
    Console.WriteLine($"|{"LEFT", -20}|");
    Console.WriteLine($"|{"RIGHT", 20}|");
    // |LEFT                |
    // |               RIGHT|
}

// https://docs.microsoft.com/ko-kr/dotnet/standard/base-types/composite-formatting#format-string-component
public static void NumberExample() {
    Console.WriteLine($"{Math.PI}");            // 3.14159265358979
    Console.WriteLine($"{Math.PI:E}");          // 3.141593E+000
    Console.WriteLine($"0x{12345:x}");          // 0x3039
    Console.WriteLine($"{Math.PI * 1000:F3}");  // 3141.593
    Console.WriteLine($"{Math.PI * 1000:N3}");  // 3,141.593
    Console.WriteLine($"{Math.PI:0000.00%}");   // 0314.16%
    Console.WriteLine($"{Math.PI:#0000.000‰}"); // 3141.593‰
}

public static void DateTimeExample() {
    // https://docs.microsoft.com/ko-kr/dotnet/standard/base-types/custom-date-and-time-format-strings
    var now = DateTime.Now;
    Console.WriteLine($"{now:yyyy-MM-dd HH:mm:ss}");    // 2019-12-16 06:15:56
    Console.WriteLine($"{now:yyyy-M-d H:m:s}");         // 2019-12-16 6:15:56
    Console.WriteLine($"{now:yy-MMM-ddd}");             // 19-Dec-Mon
    Console.WriteLine($"{now:g yy-MMMM-dddd}");         // A.D. 19-December-Monday
    Console.WriteLine($"{now:t hh:mm:ss.ffffff zzz}");  // A 06:15:56.627000 +00:00
}

enum DAY { SUN, MON, THE, WED, THU, FRI, SAT }
[Flags]
enum MODE { M1 = 0b0001, M2 = 0b0010, M3 = 0b0100, M4 = 0b1000 }

public static void EnumExample() {
    var day = DAY.WED;
    Console.WriteLine($"'{day}', 'D : {day:D}', 'X : {day:X}'");
    // 'WED', 'D : 3', 'X : 00000003'

    var mode = MODE.M2 | MODE.M4;
    Console.WriteLine($"'{mode}', 'D : {mode:D}', 'X : {mode:X}'");
    // 'M2, M4', 'D : 10', 'X : 0000000A'
}