package com.rideswift.service;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Stub for a machine-learning demand-forecasting model. A production
 * implementation would call a trained model (time-series / gradient-boosted
 * regressor on historical ride data) to predict near-future demand. Here we use
 * a transparent time-of-day heuristic — a rush-hour bump — as a placeholder so
 * the surge pipeline is fully wired and testable.
 *
 * @return predicted <em>additional</em> demand (rides) on top of {@code recentDemand}.
 */
@Service
public class DemandPredictionService {

    public double predictAdditionalDemand(long recentDemand, Instant at) {
        int hour = at.atZone(ZoneOffset.UTC).getHour();
        double rushFactor;
        if ((hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19)) {
            rushFactor = 1.5;           // morning / evening commute
        } else if (hour >= 22 || hour <= 3) {
            rushFactor = 1.2;           // late-night demand
        } else {
            rushFactor = 1.0;
        }
        return recentDemand * (rushFactor - 1.0);
    }
}
