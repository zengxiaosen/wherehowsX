/**
 * Copyright 2017 tencent. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package metadata.etl.lhotse;

import metadata.etl.lhotse.extractor.BaseLineageExtractor;
import metadata.etl.lhotse.extractor.Hive2HdfsLineageExtractor;
import metadata.etl.utils.FileOperator;
import metadata.etl.utils.SshUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wherehows.common.Constant;
import wherehows.common.LineageCombiner;
import wherehows.common.schemas.LineageRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by thomas young on 3/28/17.
 */
public class LzLineageExtractor {

    private static final Logger logger = LoggerFactory.getLogger(LzLineageExtractor.class);
    public static final String defaultLogLocation = "/usr/local/lhotse_runners/log/";

    /**
     * Reference: Get one job's lineage.
     * Process :
     * 1 get execution log from lhotse base server
     * 2 get hadoop job id from execution log
     * 3 get input, output from execution log and hadoop conf, normalize the path
     * 4 construct the Lineage Record
     *
     * @return one lhotse task's lineage
     */
    public static List<LineageRecord> extractLineage(LzExecMessage message)
            throws Exception {

        List<LineageRecord> jobLineage = new ArrayList<>();
        LzTaskExecRecord lzRecord = message.lzTaskExecRecord;
        String localLogLocation = null;

        if (message.prop.getProperty(Constant.LZ_LINEAGE_LOG_REMOTE, "false").equalsIgnoreCase("false")) {
            // the full path
            localLogLocation = message.prop.getProperty(Constant.LZ_LINEAGE_LOG_DEFAULT_DIR, defaultLogLocation);
            localLogLocation += String.format("tasklog/%d/%s", lzRecord.taskType, lzRecord.taskId);
            // find the latest file name.
            String fileName = FileOperator.getOneLogFile(localLogLocation);
            localLogLocation += "/" + fileName;
        } else {
            String remoteUser = message.prop.getProperty(Constant.LZ_REMOTE_USER_KEY);
            String remoteHost = message.prop.getProperty(Constant.LZ_REMOTE_MACHINE_KEY);
            String keyLocation = message.prop.getProperty(Constant.LZ_PRIVATE_KEY_LOCATION_KEY);
            // in remote mode, this field stands for the local dir to store the log files.
            String localLogFile = message.prop.getProperty(Constant.LZ_LINEAGE_LOG_DEFAULT_DIR);

            // move the log file from remote host to local host
            String remoteLogLocation = message.prop.getProperty(Constant.LZ_REMOTE_LOG_DIR, defaultLogLocation);
            remoteLogLocation += String.format("tasklog/%d/%s", lzRecord.taskType, lzRecord.taskId);
            // get the file list in the remote directory.
            String fileList = SshUtils.exec(remoteHost, remoteUser, keyLocation, "ls " + remoteLogLocation);
            String []files = fileList.split(" ");
            if (files.length > 0) {
                Arrays.sort(files);
            } else {
                logger.error("no log file found! task_id is: {}", message.lzTaskExecRecord.taskId);
                return jobLineage;
            }
            // prepare the remote log file.
            String remoteLogFileName = files[files.length - 1];
            String remoteLogFile = String.format("%s/%s", remoteLogLocation, remoteLogFileName);
            // prepare the local log file.
            localLogFile += String.format("tasklog/%d/%s/", lzRecord.taskType, lzRecord.taskId);
            new File(localLogFile).mkdirs();

            // fetch the remote log file to local directory.
            logger.info("local log directory is: {}", localLogFile);
            SshUtils.fileFetch(remoteHost, remoteUser, keyLocation, remoteLogFile, localLogFile);
            localLogLocation = localLogFile + remoteLogFileName;
        }

        // for debug.
        if (localLogLocation == null) {
            logger.error("log file location error!");
        } else {
            logger.info("log file to parse: {}", localLogLocation);
        }

        BaseLineageExtractor lineageExtractor = null;
        switch (lzRecord.taskType) {
            case 72:
                lineageExtractor = new Hive2HdfsLineageExtractor();
                break;
            default:
                throw new Exception("Not Supported Task Type!");
        }
        LineageCombiner lineageCombiner = new LineageCombiner(message.connection);
        Integer defaultDatabaseId = Integer.valueOf(message.prop.getProperty(Constant.LZ_DEFAULT_HADOOP_DATABASE_ID_KEY));
        if (lineageExtractor != null) {
            List<LineageRecord> lineageRecords = lineageExtractor.getLineageRecord(localLogLocation, lzRecord, defaultDatabaseId);
            try {
                logger.info("start lineage combiner.");
                lineageCombiner.addAll(lineageRecords);
                logger.info("get combined lineage.");
                jobLineage.addAll(lineageCombiner.getCombinedLineage());
            } catch (Exception e) {
                e.printStackTrace();
                logger.info(e.getMessage());
                return lineageRecords;
            }
        }
        return jobLineage;
    }

    /**
     * Extract and write to database
     * @param message
     * @throws Exception
     */
    public static void extract(LzExecMessage message)
            throws Exception {
        try{
            List<LineageRecord> result = extractLineage(message);
            for (LineageRecord lr : result) {
                message.databaseWriter.append(lr);
            }
            logger.info(String.format("%03d lineage records extracted from [%s]", result.size(), message.toString()));
            message.databaseWriter.flush();
        } catch (Exception e) {
            logger.error(String.format("Failed to extract lineage info from [%s].\n%s", message.toString(), e.getMessage()));
        }
    }
}
