package eu.kanade.tachiyomi.ui.manga.track

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding.support.v4.widget.refreshes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.RecyclerWindowInsetsListener
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.invisible
import eu.kanade.tachiyomi.util.view.visible
import kotlinx.android.synthetic.main.track_controller.*
import timber.log.Timber

class TrackController : NucleusController<TrackPresenter>(),
        TrackAdapter.OnClickListener,
        SetTrackStatusDialog.Listener,
        SetTrackChaptersDialog.Listener,
        SetTrackScoreDialog.Listener {

    private var adapter: TrackAdapter? = null

    init {
        // There's no menu, but this avoids a bug when coming from the catalogue, where the menu
        // disappears if the searchview is expanded
        setHasOptionsMenu(true)
    }

    override fun createPresenter(): TrackPresenter {
        return TrackPresenter((parentController as MangaController).manga!!)
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.track_controller, container, false)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        if ((parentController as MangaController).isLockedFromSearch) {
            swipe_refresh.invisible()
            unlock_button.visible()
            unlock_button.setOnClickListener {
                SecureActivityDelegate.promptLockIfNeeded(activity)
            }
        }

        adapter = TrackAdapter(this)
        track_recycler.layoutManager = LinearLayoutManager(view.context)
        track_recycler.adapter = adapter
        track_recycler.setOnApplyWindowInsetsListener(RecyclerWindowInsetsListener)
        swipe_refresh.isEnabled = false
        swipe_refresh.refreshes().subscribeUntilDestroy { presenter.refresh() }
    }

    private fun showTracking() {
        swipe_refresh.visible()
        unlock_button.gone()
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (!(parentController as MangaController).isLockedFromSearch) {
            showTracking()
        }
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    fun onNextTrackings(trackings: List<TrackItem>) {
        val atLeastOneLink = trackings.any { it.track != null }
        adapter?.items = trackings
        swipe_refresh?.isEnabled = atLeastOneLink
        (parentController as? MangaController)?.setTrackingIcon(atLeastOneLink)
    }

    fun onSearchResults(results: List<TrackSearch>) {
        getSearchDialog()?.onSearchResults(results)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onSearchResultsError(error: Throwable) {
        Timber.e(error)
        getSearchDialog()?.onSearchResultsError()
    }

    private fun getSearchDialog(): TrackSearchDialog? {
        return router.getControllerWithTag(TAG_SEARCH_CONTROLLER) as? TrackSearchDialog
    }

    fun onRefreshDone() {
        swipe_refresh?.isRefreshing = false
    }

    fun onRefreshError(error: Throwable) {
        swipe_refresh?.isRefreshing = false
        activity?.toast(error.message)
    }

    override fun onLogoClick(position: Int) {
        val track = adapter?.getItem(position)?.track ?: return

        if (track.tracking_url.isNullOrBlank()) {
            activity?.toast(R.string.url_not_set)
        } else {
            activity?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.tracking_url)))
        }
    }

    override fun onSetClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        TrackSearchDialog(this, item.service, item.track != null).showDialog(router,
            TAG_SEARCH_CONTROLLER)
    }

    override fun onStatusClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        SetTrackStatusDialog(this, item).showDialog(router)
    }

    override fun onChaptersClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        SetTrackChaptersDialog(this, item).showDialog(router)
    }

    override fun onScoreClick(position: Int) {
        val item = adapter?.getItem(position) ?: return
        if (item.track == null) return

        SetTrackScoreDialog(this, item).showDialog(router)
    }

    override fun setStatus(item: TrackItem, selection: Int) {
        presenter.setStatus(item, selection)
        swipe_refresh?.isRefreshing = true
    }

    override fun setScore(item: TrackItem, score: Int) {
        presenter.setScore(item, score)
        swipe_refresh?.isRefreshing = true
    }

    override fun setChaptersRead(item: TrackItem, chaptersRead: Int) {
        presenter.setLastChapterRead(item, chaptersRead)
        swipe_refresh?.isRefreshing = true
    }

    private companion object {
        const val TAG_SEARCH_CONTROLLER = "track_search_controller"
    }

}
