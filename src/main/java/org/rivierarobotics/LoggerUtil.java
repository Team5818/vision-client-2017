package org.rivierarobotics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public class LoggerUtil {

    public static Logger callingClassLogger() {
        return LoggerFactory.getLogger(Throwables
                .lazyStackTrace(new RuntimeException()).get(1).getClassName());
    }

}
