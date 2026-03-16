package com.abmo.services

import com.abmo.providers.*
import com.abmo.executor.JavaScriptExecutor
import com.abmo.util.getHost
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProviderDispatcher: KoinComponent {

    private val javaScriptExecutor: JavaScriptExecutor by inject()
    private val providerFactories: List<Pair<(String) -> Boolean, () -> Provider>> = listOf(
        Pair({ host -> host.contains("tvphim") }, { TvphimProvider(javaScriptExecutor) }),
        Pair({ host -> host == "sieutamphim.com" }, { SieutamphimProvider() }),
        Pair({ host -> host == "phimbet.biz" }, { PhimbetProvider() }),
        Pair({ host -> host.contains("motchill") || host == "subnhanh.win" }, { MotchillProvider() }),
        Pair({ host -> host == "animet3.biz" }, { Animet3Provider() }),
        Pair({ host -> host == "tvhayw.org" }, { TvhaywProvider() })
    )

    /**
     * Retrieves the appropriate provider for the given URL.
     *
     * This method examines the host part of the URL and returns an instance of the corresponding
     * provider based on the defined mappings. If the URL's host does not match any known providers,
     * it returns a default provider (AbyssToProvider).
     *
     * @param url The URL for which to find the corresponding provider.
     * @return An instance of the Provider that matches the URL's host.
     */
    fun getProviderForUrl(url: String): Provider {
        val host = url.getHost()
        return providerFactories.firstOrNull { (matches, _) -> matches(host) }?.second?.invoke()
            ?: AbyssToProvider()
    }

}
