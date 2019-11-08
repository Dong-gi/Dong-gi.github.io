package security;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;

import org.junit.jupiter.api.Test;

class SecureRandomTest {

    @Test
    void allAlgorithms() {
        for(var provider : Security.getProviders()) {
            System.out.println(provider);
            for(var service : provider.getServices()) {
                System.out.append('\t').println(service.getAlgorithm());
            }
        }
    }
    
    @Test
    void random() throws NoSuchAlgorithmException {
        SecureRandom.getInstanceStrong().longs(10).forEach(System.out::println);
    }

}
