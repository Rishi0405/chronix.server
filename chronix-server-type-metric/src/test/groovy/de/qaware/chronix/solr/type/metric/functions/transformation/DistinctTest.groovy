/*
 * Copyright (C) 2018 QAware GmbH
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package de.qaware.chronix.solr.type.metric.functions.transformation

import de.qaware.chronix.server.functions.FunctionCtx
import de.qaware.chronix.solr.type.metric.ChronixMetricTimeSeries
import de.qaware.chronix.timeseries.MetricTimeSeries
import spock.lang.Specification

/**
 * Unit test for the distinct transformation
 * @author f.lautenschlager
 */
class DistinctTest extends Specification {
    def "test transform"() {
        given:
        def timeSeriesBuilder = new MetricTimeSeries.Builder("Distinct","metric")

        10.times {
            timeSeriesBuilder.point(it * 100, it + 10)
        }

        10.times {
            timeSeriesBuilder.point(it * 100 + 1, it + 10)
        }

        10.times {
            timeSeriesBuilder.point(it * 100 + 2, it + 10)
        }

        def timeSeries = new ChronixMetricTimeSeries("", timeSeriesBuilder.build())
        def analysisResult = new FunctionCtx(1, 1, 1);

        def distinct = new Distinct();
        when:
        distinct.execute(timeSeries as List, analysisResult)
        then:
        timeSeries.getRawTimeSeries().size() == 10
        timeSeries.getRawTimeSeries().getTime(0) == 0
        timeSeries.getRawTimeSeries().getValue(0) == 10

        analysisResult.getContextFor("").getTransformation(0) == distinct
    }

    def "test getType"() {
        expect:
        new Distinct().getQueryName() == "distinct"
    }

    def "test equals and hash code"() {
        expect:
        def function = new Distinct()
        !function.equals(null)
        !function.equals(new Object())
        function.equals(function)
        function.equals(new Distinct())
        new Distinct().hashCode() == new Distinct().hashCode()
    }
}
