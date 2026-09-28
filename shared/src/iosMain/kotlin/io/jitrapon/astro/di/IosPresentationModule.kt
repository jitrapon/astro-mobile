package io.jitrapon.astro.di

import io.jitrapon.astro.presentation.calendar.CalendarScreenObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The bindings only the iOS app needs: what turns a shared observation into something Swift can
 * subscribe to.
 *
 * Android has no counterpart. It collects a view model's state in Kotlin, where a `Flow` needs no
 * adapter and nothing crosses a framework boundary.
 */
internal val iosPresentationModule: Module = module {
    // `Main`, so a Swift callback may update UI state without hopping threads itself — and not
    // `Main.immediate`, whose first dispatch would run inside `observe` before Swift holds the
    // handle it returns. A `SupervisorJob` so one subscription's end cannot end another's, and
    // cancelled on teardown so no subscription outlives the graph that served it.
    //
    // Named apart from the unqualified scope the data layer runs exchanges on — the two differ in
    // dispatcher and in what cancelling them ends, yet share a type, so only a qualifier tells them
    // apart. Written as an inline literal at each use rather than a shared `val`, because a
    // compile-time graph check can read a literal but not a value computed at runtime.
    single<CoroutineScope>(named("mainThreadDeliveryScope")) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
        .withOptions { onClose { it?.cancel() } }
    single {
        CalendarScreenObserver(
            calendarScreenRepository = get(),
            deliveryScope = get(named("mainThreadDeliveryScope")),
        )
    }
}
