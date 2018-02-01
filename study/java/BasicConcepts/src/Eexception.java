import java.io.IOException;

public class Eexception {
    public static void main(String[] args) throws Exception {
        // 독립적인 예외들이 발생하면 나중에 발생한 예외들은 addSuppressed()에 의해 억제된다.
        // 사용자 정의 예외 클래스 이름에 접미사로 Exception을 붙인다.
        // 자식은 부모보다 많은 종류의 예외를 발생시킬 수 없다.
        try {
            throw new Exception("예외 발생");
        }
        // catch, finally 문을 생략할 수 있다.
        catch (IOException ioe) {
            try {
                throw new Exception("추가 예외");
            } catch (Exception e) {
                System.out.println("처리 완료");
            }
            throw ioe; // parent method에서 예외의 나머지를 처리하도록 할 경우.
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        // finally가 catch 뒤에 나오면 예외를 처리한 뒤에 실행.
        // finally가 catch 전에 나오면 예외를 처리하기 전에 실행.
        finally {

        }
    }

}