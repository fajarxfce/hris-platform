package dev.fajar.hris.administration.delivery.requests

data class MinimumClientBuildsRequest(val android: Int, val ios: Int, val web: Int)
