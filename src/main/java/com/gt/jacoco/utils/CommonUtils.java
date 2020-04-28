package com.gt.jacoco.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
public class CommonUtils {

    public static void printShellOutput(Process exec) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exec.getInputStream()))) {
            String line;
            while((line = reader.readLine())!= null){
                log.info(line);
            }
            exec.waitFor();
            exec.destroy();
        } catch (IOException e) {
            log.error(e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
