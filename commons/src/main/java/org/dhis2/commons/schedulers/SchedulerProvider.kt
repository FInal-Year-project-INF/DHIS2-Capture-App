package org.dhis2.commons.schedulers

import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

// Interface for providing schedulers
interface SchedulerProvider {
    fun computation(): Scheduler
    fun io(): Scheduler
    fun ui(): Scheduler
}

// Concrete implementation of SchedulerProvider
class AppSchedulerProvider : SchedulerProvider {
    override fun computation(): Scheduler = Schedulers.computation()
    override fun io(): Scheduler = Schedulers.io()
    override fun ui(): Scheduler = AndroidSchedulers.mainThread()
}

// Extension function for Single
fun <T> Single<T>.defaultSubscribe(
    schedulerProvider: SchedulerProvider,
    onSuccess: (T) -> Unit? = {},
    onError: (Throwable) -> Unit? = {},
): Disposable {
    return subscribeOn(schedulerProvider.io())
        .observeOn(schedulerProvider.ui())
        .subscribe(
            { onSuccess(it) },
            { onError(it) },
        )
}

// Extension function for Observable
fun <T> Observable<T>.defaultSubscribe(
    schedulerProvider: SchedulerProvider,
    onNext: (T) -> Unit? = {},
    onError: (Throwable) -> Unit? = {},
    onComplete: () -> Unit? = {},
): Disposable {
    return subscribeOn(schedulerProvider.io())
        .observeOn(schedulerProvider.ui())
        .subscribe(
            { onNext(it) },
            { onError(it) },
            { onComplete() },
        )
}

// Extension function for Flowable
fun <T> Flowable<T>.defaultSubscribe(
    schedulerProvider: SchedulerProvider,
    onNext: (T) -> Unit? = {},
    onError: (Throwable) -> Unit? = { Timber.d(it) },
    onComplete: () -> Unit? = {},
): Disposable {
    return subscribeOn(schedulerProvider.io())
        .observeOn(schedulerProvider.ui())
        .subscribe(
            { onNext(it) },
            { onError(it) },
            { onComplete() },
        )
}
