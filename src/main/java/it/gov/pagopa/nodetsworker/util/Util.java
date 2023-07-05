package it.gov.pagopa.nodetsworker.util;

import java.util.function.Consumer;
import java.util.function.Function;

public class Util {
    public static String ifNotNull(Object o,String s){
        if(o!=null){
            return s;
        }else{
            return "";
        }
    }
    public static void ifNotNull(Object o, Function<Void,Void> func){
        if(o!=null){
            func.apply(null);
        }
    }
}
