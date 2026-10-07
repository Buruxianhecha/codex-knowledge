package com.cleo.cleos.ai

import com.cleo.cleos.data.MessageImages
import com.cleo.cleos.data.StickerBook
import com.cleo.cleos.data.StickerText
import com.cleo.cleos.data.db.MessageEntity
import com.cleo.cleos.data.db.StickerEntity

/** Resolve only pictures actually sent by the person; the sticker drawer remains text in storage. */
internal object MessagePictures {
    fun stickers(message: MessageEntity, book: StickerBook): List<StickerEntity> {
        if (message.role != "user" || message.note != null) return emptyList()
        return StickerText.split(message.content, book)
            .filterIsInstance<StickerText.Piece.Sticker>()
            .map { it.sticker }
            .distinctBy { it.file }
    }

    fun files(message: MessageEntity, book: StickerBook): List<String> {
        if (message.role != "user" || message.note != null) return emptyList()
        return MessageImages.decode(message.images).map { it.file } +
            stickers(message, book).map { it.file }
    }

    fun hasImages(history: List<MessageEntity>, book: StickerBook): Boolean =
        history.any { files(it, book).isNotEmpty() }
}
