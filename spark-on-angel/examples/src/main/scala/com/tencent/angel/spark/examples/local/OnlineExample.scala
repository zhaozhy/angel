/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in 
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/Apache-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */


package com.tencent.angel.spark.examples.local

import com.tencent.angel.ml.core.conf.{MLConf, SharedConf}
import com.tencent.angel.ml.core.utils.DataParser
import com.tencent.angel.spark.context.PSContext
import com.tencent.angel.spark.ml.core.{ArgsUtil, GraphModel, OnlineLearner}
import com.tencent.angel.spark.ml.util.SparkUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable

object OnlineExample {

  def main(args: Array[String]): Unit = {
    val params = ArgsUtil.parse(args)
    val input = params.getOrElse("input", "data/fm/census.train")
    val dataType = params.getOrElse(MLConf.ML_DATA_INPUT_FORMAT, "dummy")
    val features = params.getOrElse(MLConf.ML_FEATURE_INDEX_RANGE, "148").toInt
    val numField = params.getOrElse(MLConf.ML_FIELD_NUM, "13").toInt
    val numRank = params.getOrElse(MLConf.ML_RANK_NUM, "8").toInt
    val numEpoch = params.getOrElse(MLConf.ML_EPOCH_NUM, "200").toInt
    val fraction = params.getOrElse(MLConf.ML_BATCH_SAMPLE_RATIO, "0.1").toDouble
    val lr = params.getOrElse(MLConf.ML_LEARN_RATE, "0.02").toDouble

    val network = params.getOrElse("network", "LogisticRegression")

    SharedConf.addMap(params)
    SharedConf.get().set(MLConf.ML_DATA_INPUT_FORMAT, dataType)
    SharedConf.get().setInt(MLConf.ML_FEATURE_INDEX_RANGE, features)
    SharedConf.get().setInt(MLConf.ML_FIELD_NUM, numField)
    SharedConf.get().setInt(MLConf.ML_RANK_NUM, numRank)
    SharedConf.get().setInt(MLConf.ML_EPOCH_NUM, numEpoch)
    SharedConf.get().setDouble(MLConf.ML_BATCH_SAMPLE_RATIO, fraction)
    SharedConf.get().setDouble(MLConf.ML_LEARN_RATE, lr)


    val conf = new SparkConf()
    conf.setMaster("local[*]")
    conf.setAppName(s"$network Online Test")
    conf.set("spark.ps.model", "LOCAL")
    conf.set("spark.ps.jars", "")
    conf.set("spark.ps.instances", "1")
    conf.set("spark.ps.cores", "1")

    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(1))

    PSContext.getOrCreate(sc)

    val queue = new mutable.Queue[RDD[String]]()
    val rdd = sc.textFile(input).repartition(1)
    queue.enqueue(rdd)

    val parser = DataParser(SharedConf.get())
    val streams = ssc.queueStream(queue).map(f => parser.parse(f))

    val learner = new OnlineLearner
    val className = "com.tencent.angel.spark.ml.classification." + network
    val model = GraphModel(className)

    learner.train(streams, model, 1)

    ssc.start()
    ssc.awaitTermination()
  }

}
