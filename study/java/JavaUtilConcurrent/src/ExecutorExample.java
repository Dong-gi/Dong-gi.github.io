import java.util.concurrent.Executor;

public class ExecutorExample implements Executor {

	public static void main(String[] args) {
		var executor = new ExecutorExample();
		executor.execute(() -> {
			for(var i = 0; i < 10; ++i) {
				System.out.print("Runnable 1");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}
			}
			System.out.println();
		});
		executor.execute(() -> {
			for(var i = 0; i < 10; ++i) {
				System.out.print("Runnable 2");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}
			}
			System.out.println();
		});
		executor.execute(() -> {
			for(var i = 0; i < 10; ++i) {
				System.out.print("Runnable 3");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}
			}
			System.out.println();
		});
	}

	@Override
	public void execute(Runnable command) {
		command.run();
	}

}
