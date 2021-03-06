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
package de.qaware.chronix.solr.type.metric.functions.analyses;

import de.qaware.chronix.distance.DistanceFunction;
import de.qaware.chronix.distance.DistanceFunctionEnum;
import de.qaware.chronix.distance.DistanceFunctionFactory;
import de.qaware.chronix.dtw.FastDTW;
import de.qaware.chronix.dtw.TimeWarpInfo;
import de.qaware.chronix.server.functions.ChronixAnalysis;
import de.qaware.chronix.server.functions.FunctionCtx;
import de.qaware.chronix.server.types.ChronixTimeSeries;
import de.qaware.chronix.timeseries.MetricTimeSeries;
import de.qaware.chronix.timeseries.MultivariateTimeSeries;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The analysis implementation of the Fast DTW analysis
 * <p>
 * This analysis is called with the following cql query: metric{fastdtw:compare(field=value;field=value),5,0.4}
 *
 * @author f.lautenschlager
 */
public final class FastDtw implements ChronixAnalysis<MetricTimeSeries> {

    private DistanceFunction distanceFunction;
    private int searchRadius;
    private double maxNormalizedWarpingCost;

    //format: compare:field=value;field=value;field=value
    private Map<String, String> leftSideValues;

    private static String removeBrackets(String compareFields) {
        //remove the enfolding brackets
        //Todo make nice
        if (compareFields.startsWith("compare(") && compareFields.lastIndexOf(')') == compareFields.length() - 1) {
            return compareFields.substring("compare(".length(), compareFields.length() - 1);
        }
        return compareFields;
    }

    // original call public void execute(Pair<MetricTimeSeries, MetricTimeSeries> timeSeriesPair, FunctionCtx functionCtx) {
    @Override
    public void execute(List<ChronixTimeSeries<MetricTimeSeries>> timeSeriesList, FunctionCtx functionCtx) {

        //these time series are compared with
        List<ChronixTimeSeries<MetricTimeSeries>> leftSide = new ArrayList<>();
        //these time series
        List<ChronixTimeSeries<MetricTimeSeries>> rightSide = new ArrayList<>();

        splitTimeSeries(timeSeriesList, leftSide, rightSide);


        for (ChronixTimeSeries<MetricTimeSeries> leftSideTs : leftSide) {

            MultivariateTimeSeries compare = buildMultiVariateTimeSeries(leftSideTs);
            for (ChronixTimeSeries<MetricTimeSeries> rightSideTs : rightSide) {
                MultivariateTimeSeries with = buildMultiVariateTimeSeries(rightSideTs);

                //Call the fast dtw library
                TimeWarpInfo result = FastDTW.getWarpInfoBetween(compare, with, searchRadius, distanceFunction);
                //Check the result. If it lower equals the threshold, we can return the other time series
                functionCtx.add(this, result.getNormalizedDistance() <= maxNormalizedWarpingCost, leftSideTs.getJoinKey());
            }
        }

    }

    private void splitTimeSeries(List<ChronixTimeSeries<MetricTimeSeries>> timeSeriesList, List<ChronixTimeSeries<MetricTimeSeries>> leftSide, List<ChronixTimeSeries<MetricTimeSeries>> rightSide) {
        for (ChronixTimeSeries<MetricTimeSeries> chronixTimeSeries : timeSeriesList) {

            Map attributes = chronixTimeSeries.getAttributes();
            boolean isLeftSide = true;

            //Left side
            for (Map.Entry<String, String> field : leftSideValues.entrySet()) {
                Object value = attributes.get(field.getKey());
                //Key does not exists in time series, move it to the right
                if (value == null) {
                    isLeftSide = false;
                    break;
                }

                //Values is different, move it to the right
                if (!value.equals(field.getValue())) {
                    isLeftSide = false;
                    break;
                }

            }
            if (isLeftSide) {
                //if we get here, time series is on the left
                leftSide.add(chronixTimeSeries);
            } else {
                rightSide.add(chronixTimeSeries);
            }

        }

    }

    /**
     * Builds a multivariate time series of the given univariate time series.
     * If two or more timestamps are the same, the values are aggregated using the average.
     *
     * @param chronixTimeSeries the chronix time series
     * @return a multivariate time series for the fast dtw analysis
     */
    private MultivariateTimeSeries buildMultiVariateTimeSeries(ChronixTimeSeries<MetricTimeSeries> chronixTimeSeries) {
        MetricTimeSeries timeSeries = chronixTimeSeries.getRawTimeSeries();
        MultivariateTimeSeries multivariateTimeSeries = new MultivariateTimeSeries(1);

        if (timeSeries.size() > 0) {
            //First sort the values
            timeSeries.sort();

            long formerTimestamp = timeSeries.getTime(0);
            double formerValue = timeSeries.getValue(0);
            int timesSameTimestamp = 0;

            for (int i = 1; i < timeSeries.size(); i++) {

                //We have two timestamps that are the same
                if (formerTimestamp == timeSeries.getTime(i)) {
                    formerValue += timeSeries.getValue(i);
                    timesSameTimestamp++;
                } else {
                    //calc the average of the values of the same timestamp
                    if (timesSameTimestamp > 0) {
                        formerValue = formerValue / timesSameTimestamp;
                        timesSameTimestamp = 0;
                    }
                    //first add the former timestamp
                    multivariateTimeSeries.add(formerTimestamp, new double[]{formerValue});
                    formerTimestamp = timeSeries.getTime(i);
                    formerValue = timeSeries.getValue(i);
                }
            }
            //add the last point
            multivariateTimeSeries.add(formerTimestamp, new double[]{formerValue});
        }

        return multivariateTimeSeries;
    }

    @Override
    public void setArguments(String[] args) {

        //Used to split the list with time series into two groups.
        this.leftSideValues = splitFieldValues(args[0]);

        this.searchRadius = Integer.parseInt(args[1]);
        this.maxNormalizedWarpingCost = Double.parseDouble(args[2]);

        //Make this configurable some time.
        this.distanceFunction = DistanceFunctionFactory.getDistanceFunction(DistanceFunctionEnum.EUCLIDEAN);
    }

    private Map<String, String> splitFieldValues(String fieldValues) {
        String[] fieldValuePairs = removeBrackets(fieldValues).split(";");
        Map<String, String> result = new HashMap<>(fieldValuePairs.length);

        for (String fieldValuePair : fieldValuePairs) {
            String[] fieldAndValue = fieldValuePair.split("=");
            result.put(fieldAndValue[0], fieldAndValue[1]);
        }

        return result;
    }

    @Override
    public String[] getArguments() {
        return new String[]{"search radius=" + searchRadius,
                "max warping cost=" + maxNormalizedWarpingCost,
                "distance function=" + DistanceFunctionEnum.EUCLIDEAN.name()};
    }

    @Override
    public String getQueryName() {
        return "fastdtw";
    }

    @Override
    public String getType() {
        return "metric";
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
        FastDtw rhs = (FastDtw) obj;
        return new EqualsBuilder()
                .append(this.distanceFunction, rhs.distanceFunction)
                .append(this.searchRadius, rhs.searchRadius)
                .append(this.maxNormalizedWarpingCost, rhs.maxNormalizedWarpingCost)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(distanceFunction)
                .append(searchRadius)
                .append(maxNormalizedWarpingCost)
                .toHashCode();
    }

    @Override
    public String toString() {
        return "FastDtw{" +
                "distanceFunction=" + distanceFunction +
                ", searchRadius=" + searchRadius +
                ", maxNormalizedWarpingCost=" + maxNormalizedWarpingCost +
                '}';
    }
}
