package com.rickb.imagepicker.adapter

import android.content.Context
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.rickb.imagepicker.R
import com.rickb.imagepicker.extension.debounceClicks
import com.rickb.imagepicker.features.imageloader.ImageLoader
import com.rickb.imagepicker.features.imageloader.ImageType
import com.rickb.imagepicker.helper.ImagePickerUtils
import com.rickb.imagepicker.helper.addCompressedFile
import com.rickb.imagepicker.listeners.ActionHandler
import com.rickb.imagepicker.listeners.OnImageSelectedListener
import com.rickb.imagepicker.listeners.OnTotalSizeLimitReachedListener
import com.rickb.imagepicker.model.Image
import com.rickb.imagepicker.model.ImageQuality
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.ef_imagepicker_header.view.*
import kotlinx.android.synthetic.main.ef_imagepicker_item_image.view.*
import java.io.File
import java.util.*

class ImagePickerAdapter(
        context: Context,
        imageLoader: ImageLoader,
        selectedImages: List<Image>,
        private val actionHandler: ActionHandler,
        private val maxTotalSizeLimit: Double?,
        private val maxTotalSelectionsLimit: Int?,
        private val publicAppDirectory: String?,
        private val imageQuality: ImageQuality?
) : BaseListAdapter<ImagePickerAdapter.BaseImagePickerViewHolder>(context, imageLoader) {
    /**
     * Disposables that will be disposed of during [.onDestroy]
     */
    protected var disposables = CompositeDisposable()

    private val items: MutableList<PickerItem> = mutableListOf()
    val selectedImages: MutableList<Image> = mutableListOf()

    private var imageSelectedListener: OnImageSelectedListener? = null
    private var onTotalSizeLimitReachedListener: OnTotalSizeLimitReachedListener? = null
    private val videoDurationHolder = HashMap<Long, String?>()

    /**
     * Amount of pages with images that are added.
     */
    private var nextPageAdded = 0

    private val showingImages: MutableList<Image> = emptyList<Image>().toMutableList()
    private var nextPageIndex = 0

    private var wasTotalSizeLimitReached = false
    var wasTotalSelectionLimitReached = false

    init {
        if (selectedImages.isNotEmpty()) {
            this.selectedImages.addAll(selectedImages)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        disposables = CompositeDisposable()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        disposables.dispose()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseImagePickerViewHolder {
        if (viewType == VIEW_TYPE_HEADER) {
            val layout = inflater.inflate(
                    R.layout.ef_imagepicker_header,
                    parent,
                    false
            )
            return HeaderViewHolder(layout)
        } else {
            val layout = inflater.inflate(
                    R.layout.ef_imagepicker_item_image,
                    parent,
                    false
            )
            return ImageViewHolder(layout)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is ImageHeaderPlaceHolder) {
            VIEW_TYPE_HEADER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    override fun onBindViewHolder(viewHolder: BaseImagePickerViewHolder, position: Int) {
        val viewType = getItemViewType(position)
        if (viewType == VIEW_TYPE_HEADER) {
            onBindHeaderViewHolder(viewHolder as HeaderViewHolder, position)
        } else if (viewType == VIEW_TYPE_ITEM) {
            val image = items[position] as Image
            onBindImageViewHolder(viewHolder as ImageViewHolder, image)
        } else throw IllegalStateException("Unimplemented viewType for item at position $position.")
    }

    private fun onBindHeaderViewHolder(viewHolder: HeaderViewHolder, position: Int) {
        val headerItem = items[position] as ImageHeaderPlaceHolder
        val context = viewHolder.textView.context
        viewHolder.apply {
            val weeksAgo = headerItem.weeksAgo
            textView.text = when (weeksAgo) {
                0 -> {
                    if (headerItem.isRecent) {
                        context.getString(R.string.ef_header_recent)
                    } else {
                        context.getString(R.string.ef_header_last_week)
                    }
                }
                1 -> context.getString(R.string.ef_header_1_week_ago)
                else -> context.getString(R.string.ef_header_weeks_ago, weeksAgo)
            }
        }
    }

    private fun onBindImageViewHolder(viewHolder: ImageViewHolder, image: Image) {
        val isSelected = isSelected(image)
        imageLoader.loadImage(image, viewHolder.imageView, ImageType.GALLERY)

        var showFileTypeIndicator = false
        var fileTypeLabel: String? = ""

        if (ImagePickerUtils.isGifFormat(image)) {
            fileTypeLabel = context.resources.getString(R.string.ef_gif)
            showFileTypeIndicator = true
        }

        if (ImagePickerUtils.isVideoFormat(image)) {
            if (!videoDurationHolder.containsKey(image.id)) {
                videoDurationHolder[image.id] = ImagePickerUtils.getVideoDurationLabel(
                        context, File(image.path)
                )
            }

            fileTypeLabel = videoDurationHolder[image.id]
            showFileTypeIndicator = true
        }

        viewHolder.apply {
            fileTypeIndicator.text = fileTypeLabel
            fileTypeIndicator.visibility = if (showFileTypeIndicator) View.VISIBLE else View.GONE

            val backgroundColorResId = if (isSelected) R.color.ef_black else R.color.ef_white
            alphaView.setBackgroundColor(ResourcesCompat.getColor(context.resources, backgroundColorResId, null))
            alphaView.alpha = if (isSelected || isMaxTotalSizeReached() || isMaxTotalSelectionsReached()) 0.5f else 0f

            itemView.debounceClicks().observe {
                if (image.id == CAMERA_ICON_ID) {
                    if (isMaxTotalSizeReached()) return@observe
                    if (isMaxTotalSelectionsReached()) return@observe
                    actionHandler.requestCameraImage()
                    return@observe
                }
                val shouldSelect = actionHandler.onImageClick(isSelected)

                if (isSelected) {
                    removeSelectedImage(image, position)
                } else if (shouldSelect) {
                    addSelected(viewHolder.imageView.context, image, position)
                }
            }
            container?.foreground = if (isSelected) ContextCompat.getDrawable(
                    context,
                    R.drawable.ef_ic_done_white
            ) else null
        }
    }

    private fun isSelected(image: Image): Boolean {
        return selectedImages.any { it.id == image.id }
    }

    override fun getItemCount() = items.size

    fun setData(images: List<Image>) {
        nextPageIndex = 0
        nextPageAdded = 0
        setFirstPage(images)
    }

    private fun setFirstPage(images: List<Image>) {
        // Make sure the indexes are not outside bounds of allImages.
        val pageStartIndex = (images.size - 1).coerceAtMost(nextPageIndex * PAGE_SIZE)
        val pageEndIndex = (images.size).coerceAtMost(((nextPageIndex + 1) * PAGE_SIZE))
        val page = images.subList(pageStartIndex, pageEndIndex)
        showingImages.addAll(page)

        updateSelectedImages(showingImages)

        val imagesAndHeadersAndCameraIcon = addHeaders(showingImages)
                .run {
                    addCameraIcon(this)
                }

        this.items.clear()
        this.items.addAll(imagesAndHeadersAndCameraIcon)
        notifyDataSetChanged()
    }

    fun addNextPage(images: List<Image>, page: Int) {
        val imagesAndHeaders = addHeaders(images)
        this.items.addAll(imagesAndHeaders)

        // Notify all changed because notifyItemRangeChanged(newRangeStart, images.size) had issues with scrolling further after new items were added.
        notifyDataSetChanged()
        nextPageAdded = page
    }

    fun requestNextPage() {
        // Next page already requested but not added yet.
        if (nextPageAdded > nextPageIndex) return

        nextPageIndex++
        actionHandler.requestNextPage(nextPageIndex)
    }

    private fun updateSelectedImages(images: List<Image>) {
        selectedImages.toList().forEachIndexed { index, image ->
            val updatedImage = images
                    .find { it.id == image.id }
                    ?.apply {
                        compressedFilePath = image.compressedFilePath
                    }
                    ?: image
            selectedImages[index] = updatedImage
        }
    }

    private fun addHeaders(images: List<Image>): List<PickerItem> = mutableListOf<PickerItem>()
            .apply {
                var lastAddedHeaderTimeStamp = items.lastOrNull()?.weeksAgo ?: 0 // 0

                // If there are recent images this will be set to true until the first not-recent image is added (and so the 'last week' header is added).
                var needsAdditionalHeader = false

                images
                        .forEachIndexed { index, image ->
                            val weeksAgo = image.weeksAgo
                            val isDifferentWeeksAgo = weeksAgo != lastAddedHeaderTimeStamp

                            // Add a header item before index 0 and for each image that is more than the amount of weeks ago of the previopusly added header.
                            if (index == 0 && items.isEmpty()) {
                                if (image.isRecent) {
                                    needsAdditionalHeader = true
                                }
                                add(ImageHeaderPlaceHolder(image.lastChangedTimestamp))
                                lastAddedHeaderTimeStamp = weeksAgo
                            } else if (isDifferentWeeksAgo) {
                                add(ImageHeaderPlaceHolder(image.lastChangedTimestamp))
                                lastAddedHeaderTimeStamp = weeksAgo
                            }

                            if (needsAdditionalHeader && !image.isRecent) {
                                // the first header will be the 'Recent' header. Add another header before the first item that is not recent.
                                add(ImageHeaderPlaceHolder(image.lastChangedTimestamp))
                                lastAddedHeaderTimeStamp = weeksAgo
                                needsAdditionalHeader = false
                            }

                            add(image)
                        }
            }

    private fun addCameraIcon(images: List<PickerItem>): List<PickerItem> {
        // Add the camera icon after at first position (but after the first header)
        val firstHeaderIndex = images.indexOfFirst { it is ImageHeaderPlaceHolder }

        val lastRecentTime = images.getOrNull(firstHeaderIndex + 1)?.lastChangedTimestamp?.apply {
            this - 1L
        } ?: 0
        val cameraIconTimeAdjusted = createCameraIcon(lastRecentTime)

        return images.toMutableList().apply {
            add(firstHeaderIndex + 1, cameraIconTimeAdjusted)
        }
    }

    private fun addSelected(context: Context, image: Image, position: Int) {
        mutateSelection {
            publicAppDirectory?.let {
                // only add compressed file if it wasn't present yet.
                if (image.compressedFilePath == null) {
                    addCompressedFile(context, image, it, imageQuality)
                }
            }

            selectedImages.add(image)
            notifyItemChanged(position)
        }
    }

    private fun removeSelectedImage(image: Image, position: Int) {
        mutateSelection {
            selectedImages.remove(image)
            notifyItemChanged(position)
        }
    }

    fun removeAllSelectedSingleClick() {
        mutateSelection {
            selectedImages.clear()
            notifyDataSetChanged()
        }
    }

    private fun mutateSelection(runnable: Runnable) {
        runnable.run()
        if (!isMaxTotalSizeReached()) {
            imageSelectedListener?.onSelectionUpdate(selectedImages)
        } else {
            onTotalSizeLimitReachedListener?.onTotalSizeLimitReached()
        }
        // Update all items after a selection change, only if the total limit was reached and now is not anymore OR the total limit was not reached but now is.
        if (wasTotalSizeLimitReached xor isMaxTotalSizeReached()) {
            wasTotalSizeLimitReached = isMaxTotalSizeReached()
            notifyDataSetChanged()
        }
        if (wasTotalSelectionLimitReached xor isMaxTotalSelectionsReached()) {
            wasTotalSelectionLimitReached = isMaxTotalSelectionsReached()
            notifyDataSetChanged()
        }
    }

    fun isMaxTotalSizeReached() =
            getTotalSelectedFileSize() > (maxTotalSizeLimit
                    ?: Double.MAX_VALUE) * MB_TO_BYTES_CONVERSION_MULTIPLIER

    private fun isMaxTotalSelectionsReached() =
            selectedImages.size >= (maxTotalSelectionsLimit ?: Integer.MAX_VALUE)

    /**
     * @return the file size in bytes of all selected media together.
     */
    private fun getTotalSelectedFileSize(): Long {
        var totalFileSize: Long = 0
        for (image in selectedImages) {
            image.compressedFilePath?.let {
                val file = File(it)
                totalFileSize += file.length()
            }
        }
        return totalFileSize
    }

    fun setImageSelectedListener(imageSelectedListener: OnImageSelectedListener?) {
        this.imageSelectedListener = imageSelectedListener
    }

    fun setOnTotalSizeLimitReachedListener(onTotalSizeLimitReachedListener: OnTotalSizeLimitReachedListener?) {
        this.onTotalSizeLimitReachedListener = onTotalSizeLimitReachedListener
    }

    class HeaderViewHolder(itemView: View) : BaseImagePickerViewHolder(itemView) {
        val textView: TextView = itemView.header_text_view
    }

    class ImageViewHolder(itemView: View) : BaseImagePickerViewHolder(itemView) {
        val imageView: ImageView = itemView.image_view
        val alphaView: View = itemView.view_alpha
        val fileTypeIndicator: TextView = itemView.ef_item_file_type_indicator
        val container: FrameLayout? = itemView as? FrameLayout
    }

    open class BaseImagePickerViewHolder(itemView: View) : ViewHolder(itemView)

    /**
     * Subscribe and add to disposables.
     *
     * @param consumer the consumer
     */
    fun <T> Observable<T>.observe(consumer: (T) -> Unit) {
        disposables.add(subscribe(consumer, {
            Log.e(this.javaClass.simpleName, it.message ?: "error received while observing.")
        }))
    }

    /**
     * Creates the Image model for the camera icon.
     * @param lastChangedTimestamp needs to be a tiny bit before the first actual photo from gallery, so that it appears at the first item under "recent" header.
     */
    private fun createCameraIcon(lastChangedTimestamp: Long) = Image(CAMERA_ICON_ID, "camera", "", null, lastChangedTimestamp)

    /**
     * Deletes all compressed images that were created. Should be called onDestroy.
     * @param shouldAlsoDeleteSelected Whether the selected compressed images should also be deleted.
     */
    fun deleteCompressedImages(shouldAlsoDeleteSelected: Boolean, exclude: List<Image>) {
        items
                .filterIsInstance(Image::class.java)
                .filter { image ->
                    if (shouldAlsoDeleteSelected) true
                    else selectedImages.find { it.id == image.id } == null
                }
                .filter { image ->
                    exclude.find { it.id == image.id } == null
                }
                .map {
                    it.compressedFilePath
                }.forEach { path ->
                    path?.let {
                        deleteFile(it)
                    }
                }
    }

    private fun deleteFile(path: String) {
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }

    companion object {
        // 1 MB = 1048576 Bytes.
        const val MB_TO_BYTES_CONVERSION_MULTIPLIER = 1048576

        const val VIEW_TYPE_ITEM = 1
        const val VIEW_TYPE_HEADER = 2

        private const val MILLIS_IN_DAY = 1000 * 60 * 60 * 24
        private const val MILLIS_IN_WEEK = MILLIS_IN_DAY * 7

        const val CAMERA_ICON_ID = 83621L

        /**
         * Amount of images in a page that will be added to the itemset at once.
         */
        private const val PAGE_SIZE = 500

        @Parcelize
        open class PickerItem(open val lastChangedTimestamp: Long) : Parcelable {
            /**
             * How many weeks ago this image's file was last edited.
             */
            val weeksAgo: Int
                get() {
                    return ((System.currentTimeMillis() - lastChangedTimestamp) / MILLIS_IN_WEEK).toInt()
                }

            val isRecent: Boolean
                get() {
                    val daysAgo = ((System.currentTimeMillis() - lastChangedTimestamp) / MILLIS_IN_DAY).toInt()
                    return daysAgo <= Image.MAX_DAYS_AGO_FOR_RECENT
                }
        }

        @Parcelize
        class ImageHeaderPlaceHolder(val timestamp: Long) : PickerItem(timestamp)
    }
}