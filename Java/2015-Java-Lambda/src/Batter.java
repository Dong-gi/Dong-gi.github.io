
public class Batter implements Comparable<Batter> {
	private String name;
	private int number;
	private double batAverage;
	public Batter(String name, int number, double batAverage) {
		this.name = name;
		this.number = number;
		this.batAverage = batAverage;
	}
	public String getName(){
		return name;
	}
	public int getNumber(){
		return number;
	}
	public double getBatAverage(){
		return batAverage;
	}
	public String toString(){
		return String.format("(%d) %s: %.3f", number, name, batAverage);
	}
	@Override
	public int compareTo(Batter o) {
		return name.compareTo(o.name);
	}
}
