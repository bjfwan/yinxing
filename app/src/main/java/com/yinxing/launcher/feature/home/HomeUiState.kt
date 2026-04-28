package com.yinxing.launcher.feature.home

sealed interface HomeUiState {
    val items: List<HomeAppItem>

    data class Loading(
        override val items: List<HomeAppItem>
    ) : HomeUiState

    data class Success(
        override val items: List<HomeAppItem>
    ) : HomeUiState

    data class Empty(
        override val items: List<HomeAppItem>
    ) : HomeUiState

    data class Error(
        override val items: List<HomeAppItem>,
        val message: String
    ) : HomeUiState
}
