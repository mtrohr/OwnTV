package tv.own.owntv.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.player.OwnTVPlayer

/** App-wide libmpv player. */
val playerModule = module {
    // context, settings, connectivity
    single { OwnTVPlayer(androidContext(), get(), get()) }
}
