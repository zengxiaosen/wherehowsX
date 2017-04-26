# author: thomas young 26/4/2017

from wherehows.common.schemas import LhotseFlowRecord
from wherehows.common.writers import FileWriter
from wherehows.common import Constant
from wherehows.common.utils import DateFormater
from wherehows.common.enums import SchedulerType
from com.ziclix.python.sql import zxJDBC
from org.slf4j import LoggerFactory
import os, sys, json, gzip
import StringIO
import datetime, time
import DbUtil


class LhotseExtract:

    _period_unit_table = {'d': 'DAY',
                          'M': 'MONTH',
                          'h': 'HOUR',
                          'm': 'MINUTE',
                          'w': 'WEEK'}

    def __init__(self, args):
        self.logger = LoggerFactory.getLogger('jython script : ' + self.__class__.__name__)
        self.app_id = int(args[Constant.APP_ID_KEY])
        self.wh_exec_id = long(args[Constant.WH_EXEC_ID_KEY])
        self.lz_con = zxJDBC.connect(args[Constant.LZ_DB_URL_KEY],
                                     args[Constant.LZ_DB_USERNAME_KEY],
                                     args[Constant.LZ_DB_PASSWORD_KEY],
                                     args[Constant.LZ_DB_DRIVER_KEY])
        self.lz_cursor = self.lz_con.cursor()
        self.lookback_period = args[Constant.LZ_EXEC_ETL_LOOKBACK_MINS_KEY]
        self.app_folder = args[Constant.WH_APP_FOLDER_KEY]
        self.metadata_folder = self.app_folder + "/" + str(SchedulerType.LHOTSE) + "/" + str(self.app_id)

        if not os.path.exists(self.metadata_folder):
            try:
                os.makedirs(self.metadata_folder)
            except Exception as e:
                self.logger.error(e)

    def run(self):
        self.logger.info("Begin Lhotse Extract")
        try:
            # to do list
            self.collect_flow_jobs(self.metadata_folder + "/flow.csv", self.metadata_folder + "/job.csv", self.metadata_folder + "/dag.csv")
        finally:
            self.lz_cursor.close()
            self.lz_con.close()

    def collect_flow_jobs(self, flow_file, job_file, dag_file):
        self.logger.info("collect flow&jobs")
        query = "SELECT distinct * FROM workflow_info WHERE status = NULL"
        self.az_cursor.execute(query)
        rows = DbUtil.dict_cursor(self.az_cursor)
        flow_writer = FileWriter(flow_file)
        job_writer = FileWriter(job_file)
        dag_writer = FileWriter(dag_file)
        row_count = 0

        for row in rows:
            row['version'] = 0 if (row["version"] is None) else row["version"]

            flow_path = row['project_name'] + ":" + row['workflow_name']

            flow_record = LhotseFlowRecord(self.app_id,
                                            row['workflow_name'],
                                            row['project_name'],
                                            flow_path,
                                            0,
                                            DateFormater.getInt(row['modify_time']),
                                            row["version"],
                                            'Y',
                                            self.wh_exec_id)
            flow_writer.append(flow_record)

            row_count += 1

            if row_count % 1000 == 0:
                flow_writer.flush()
                job_writer.flush()
                dag_writer.flush()

        flow_writer.close()
        job_writer.close()
        dag_writer.close()

if __name__ == "__main__":
    props = sys.argv[1]
    lz = LhotseExtract(props)
    lz.run()