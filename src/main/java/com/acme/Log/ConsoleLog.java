package com.acme.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author acme
 * @date 2019/8/26 7:36 PM
 */
public class ConsoleLog implements Log{

    @Override
    public void debug(String format, Object... args) {
        log(Log.DEBUG, format, args);
    }

    @Override
    public void info(String format, Object... args) {
        log(Log.INFO, format, args);
    }

    @Override
    public void warn(String format, Object... args) {
        log(Log.WARN, format, args);
    }

    @Override
    public void error(String format, Object... args) {
        log(Log.ERROR, format, args);
    }

    private void log(String type, String format, Object... args){
        StringBuilder sb = new StringBuilder(format);
        int pos = -1;
        for (int i = 0;i < args.length;i++){
            pos = sb.indexOf("{}", pos + 1);
            if(args[i] == null){
                args[i] = "null";
            }
            sb.replace(pos, pos + 2, args[i].toString());
        }
        if(Log.INFO.equals(type)){
            System.out.println(type + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + sb);
        } else {
            System.err.println(type + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + sb);
        }
    }
}
