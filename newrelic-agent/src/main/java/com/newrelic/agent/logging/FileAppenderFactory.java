/*
 *
 *  * Copyright 2020 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.newrelic.agent.logging;

import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.FileManager;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.NoOpTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.newrelic.agent.logging.Log4jLogger.CONVERSION_PATTERN;

public class FileAppenderFactory {

    /**
     * The minimum number of files.
     */
    private static final int MIN_FILE_COUNT = 1;

    /**
     * The default is to append to the file.
     */
    private static final boolean APPEND_TO_FILE = true;
    private static final String DAILY_CRON = "0 0 0 * * ?";
    /**
     * The name of the file appender.
     */
    static final String FILE_APPENDER_NAME = "File";

    /**
     * Initial delay for scheduling log files cleanup task (in seconds).
     */
    private static final long INITIAL_DELAY_SECONDS = 60;

    /**
     * Repeat interval for scheduling log files cleanup task (in seconds).
     */
    private static final int REPEAT_INTERVAL_SECONDS = 24 * 60 * 60;


    private final int fileCount;
    private final long logLimitBytes;
    private final String fileName;
    private final boolean isDaily;
    private final String path;

    /**
     * @param fileCount maximum number of log files
     * @param logLimitBytes maximum size of a given log file
     * @param fileName prefix for log file names
     * @param isDaily if the logs are to be rolled over daily
     * @param path directory path for log files
     */
    public FileAppenderFactory(int fileCount, long logLimitBytes, String fileName, boolean isDaily, String path) {
        this.fileCount = fileCount;
        this.logLimitBytes = logLimitBytes;
        this.fileName = fileName;
        this.isDaily = isDaily;
        this.path = path;
    }

    /**
     * Create a full initialized FileAppender with a {@link TriggeringPolicy} set based on the configuration.
     *
     * @return file appender to log to
     */
    AbstractOutputStreamAppender<? extends FileManager> build() {
        AbstractOutputStreamAppender<? extends FileManager> rollingFileAppender = buildRollingFileAppender();
        rollingFileAppender.start();
        return rollingFileAppender;
    }

    private AbstractOutputStreamAppender<? extends FileManager> buildRollingFileAppender() {
        if (isDaily) {
            return buildDailyRollingAppender();
        }

        if (logLimitBytes > 0) {
            return initializeRollingFileAppender()
                    .withStrategy(DefaultRolloverStrategy.newBuilder()
                            .withMin(String.valueOf(MIN_FILE_COUNT))
                            .withMax(String.valueOf(Math.max(1, fileCount)))
                            .build())
                    .withPolicy(sizeBasedPolicy())
                    .withFilePattern(fileName + ".%i")
                    .build();
        }

        return buildDefaultFileAppender(fileName);
    }

    private AbstractOutputStreamAppender<? extends FileManager> buildDefaultFileAppender(String fileName) {
        return ((FileAppender.Builder) FileAppender.newBuilder()
                .withFileName(fileName)
                .withAppend(APPEND_TO_FILE)
                .setName(FILE_APPENDER_NAME)
                .setLayout(PatternLayout.newBuilder().withPattern(CONVERSION_PATTERN).build()))
                .build();
    }

    private RollingFileAppender buildDailyRollingAppender() {

        TriggeringPolicy policy = buildRollingAppenderTriggeringPolicy();
        DefaultRolloverStrategy rolloverStrategy = DefaultRolloverStrategy.newBuilder()
                .withMax(String.valueOf(fileCount))
                .build();

        String filePattern = fileName + ".%d{yyyy-MM-dd}";
        if (logLimitBytes > 0) {
            // If we might roll within a day, use a number ordering suffix
            filePattern = fileName + ".%d{yyyy-MM-dd}.%i";
        }

        Path directory = new File(this.path).toPath();
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("New Relic Expiring Log File Cleanup");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(
                new ClearExpiredLogsRunnable(directory, fileCount, fileName),
                INITIAL_DELAY_SECONDS,
                REPEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        return initializeRollingFileAppender()
                .withPolicy(policy)
                .withFilePattern(filePattern)
                .withStrategy(rolloverStrategy)
                .build();
    }

    private TriggeringPolicy buildRollingAppenderTriggeringPolicy() {
        TimeBasedTriggeringPolicy timeBasedTriggeringPolicy = TimeBasedTriggeringPolicy.newBuilder().withInterval(1).withModulate(true).build();
        TriggeringPolicy sizeBasedTriggeringPolicy = sizeBasedPolicy();
        return CompositeTriggeringPolicy.createPolicy(timeBasedTriggeringPolicy, sizeBasedTriggeringPolicy);
    }

    private RollingFileAppender.Builder initializeRollingFileAppender() {
        return (RollingFileAppender.Builder) RollingFileAppender.newBuilder()
                .withFileName(fileName)
                .withAppend(APPEND_TO_FILE)
                .setName(FILE_APPENDER_NAME)
                .setLayout(PatternLayout.newBuilder().withPattern(CONVERSION_PATTERN).build());
    }

    private TriggeringPolicy sizeBasedPolicy() {
        return (logLimitBytes > 0) ?
                SizeBasedTriggeringPolicy.createPolicy(String.valueOf(logLimitBytes)) :
                NoOpTriggeringPolicy.createPolicy();
    }

}
