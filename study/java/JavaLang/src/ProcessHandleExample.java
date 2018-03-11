import java.util.Optional;

public class ProcessHandleExample {

	private static long pid = 0;
	
	public static void main(String[] args) throws Exception {
		ProcessBuilder pb = new ProcessBuilder("notepad", "");
		ProcessHandle p1 = pb.start().toHandle();
		
		ProcessHandle.allProcesses().forEach((handle) -> {
			ProcessHandle.Info info = handle.info();
			System.out.println(
					new StringBuilder("pid : ")
					.append(handle.pid())
					.append(" command : ")
					.append(info.command())
					.append(" cpu duration : ")
					.append(info.totalCpuDuration()));
			if(info.command().toString().contains("notepad.exe")) {
				pid = handle.pid();
			}
		});
		
		Optional<ProcessHandle> p2 = ProcessHandle.of(pid);
		System.out.println("\n\n" + p1.equals(p2.get()));
		
		p1.onExit().thenRun(() -> System.out.println("notepad closed"));
		System.out.println(p1.onExit().get());
	}

}
