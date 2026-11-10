package com.yachiqo.spotgo.parking.application;
import java.util.UUID;
/** Current physical observation, distinct from future reservation availability. */
public interface ParkingOccupancyGateway { String physicalOccupancy(UUID spotId); }
