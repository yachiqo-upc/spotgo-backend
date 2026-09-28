package com.yachiqo.spotgo.parking.domain.model.commands;

import com.yachiqo.spotgo.parking.domain.model.valueobjects.ReportStatus;

public record UpdateClientReportStatusCommand(String reportId, ReportStatus status) {
}
