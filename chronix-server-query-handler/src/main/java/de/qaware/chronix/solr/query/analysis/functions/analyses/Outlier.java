/*
 * Copyright (C) 2016 QAware GmbH
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
package de.qaware.chronix.solr.query.analysis.functions.analyses;

import de.qaware.chronix.converter.common.DoubleList;
import de.qaware.chronix.solr.query.analysis.FunctionValueMap;
import de.qaware.chronix.solr.query.analysis.functions.ChronixAnalysis;
import de.qaware.chronix.solr.query.analysis.functions.FunctionType;
import de.qaware.chronix.solr.query.analysis.functions.math.Percentile;
import de.qaware.chronix.timeseries.MetricTimeSeries;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * The outlier analysis
 *
 * @author f.lautenschlager
 */
public class Outlier implements ChronixAnalysis<MetricTimeSeries> {


    /**
     * Detects outliers using the default box plot implementation.
     * An outlier every value that is above (q3-q1)*1.5*q3 where qN is the nth percentile
     *
     * @param analysisAndValues
     */
    @Override
    public void execute(MetricTimeSeries timeSeries, FunctionValueMap analysisAndValues) {

        if (timeSeries.isEmpty()) {
            //TODO: Add result
            analysisAndValues.add(this, false, null);
            return;
        }

        DoubleList points = timeSeries.getValues();
        //Calculate the percentiles
        double q1 = Percentile.evaluate(points, .25);
        double q3 = Percentile.evaluate(points, .75);
        //Calculate the threshold
        double threshold = (q3 - q1) * 1.5 + q3;
        //filter the values, if one outlier is found, we can return
        for (int i = 0; i < points.size(); i++) {
            double point = points.get(i);
            if (point > threshold) {
                analysisAndValues.add(this, true, null);
                return;
            }
        }
        analysisAndValues.add(this, false, null);
    }


    @Override
    public String[] getArguments() {
        return new String[0];
    }

    @Override
    public FunctionType getType() {
        return FunctionType.OUTLIER;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        return new EqualsBuilder()
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .toHashCode();
    }
}
