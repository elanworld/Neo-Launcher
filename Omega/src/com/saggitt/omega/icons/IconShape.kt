/*
 *  This file is part of Omega Launcher
 *  Copyright (c) 2021   Omega Launcher Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saggitt.omega.icons

import android.graphics.Path
import android.graphics.PointF
import com.android.launcher3.R
import com.android.launcher3.Utilities

open class IconShape(
    val topLeft: Corner,
    val topRight: Corner,
    val bottomLeft: Corner,
    val bottomRight: Corner
) {

    constructor(
        topLeftShape: IconCornerShape,
        topRightShape: IconCornerShape,
        bottomLeftShape: IconCornerShape,
        bottomRightShape: IconCornerShape,
        topLeftScale: Float,
        topRightScale: Float,
        bottomLeftScale: Float,
        bottomRightScale: Float
    ) : this(
        Corner(topLeftShape, topLeftScale),
        Corner(topRightShape, topRightScale),
        Corner(bottomLeftShape, bottomLeftScale),
        Corner(bottomRightShape, bottomRightScale)
    )

    constructor(
        topLeftShape: IconCornerShape,
        topRightShape: IconCornerShape,
        bottomLeftShape: IconCornerShape,
        bottomRightShape: IconCornerShape,
        topLeftScale: PointF,
        topRightScale: PointF,
        bottomLeftScale: PointF,
        bottomRightScale: PointF
    ) : this(
        Corner(topLeftShape, topLeftScale),
        Corner(topRightShape, topRightScale),
        Corner(bottomLeftShape, bottomLeftScale),
        Corner(bottomRightShape, bottomRightScale)
    )

    constructor(shape: IconShape) : this(
        shape.topLeft, shape.topRight, shape.bottomLeft, shape.bottomRight
    )

    private val isCircle =
        topLeft == Corner.fullArc &&
                topRight == Corner.fullArc &&
                bottomLeft == Corner.fullArc &&
                bottomRight == Corner.fullArc

    private val tmpPoint = PointF()
    open val qsbEdgeRadius = 0
    open val windowTransitionRadius = 1f

    open fun getMaskPath(): Path {
        return Path().also { addToPath(it, 0f, 0f, 100f, 100f, 50f) }
    }

    open fun addShape(path: Path, x: Float, y: Float, radius: Float) {
        if (isCircle) {
            path.addCircle(x + radius, y + radius, radius, Path.Direction.CW)
        } else {
            val size = radius * 2
            addToPath(path, x, y, x + size, y + size, radius)
        }
    }

    @JvmOverloads
    fun addToPath(
        path: Path, left: Float, top: Float, right: Float, bottom: Float,
        size: Float = 50f, endSize: Float = size, progress: Float = 0f
    ) {
        val topLeftSizeX = Utilities.mapRange(progress, topLeft.scale.x * size, endSize)
        val topLeftSizeY = Utilities.mapRange(progress, topLeft.scale.y * size, endSize)
        val topRightSizeX = Utilities.mapRange(progress, topRight.scale.x * size, endSize)
        val topRightSizeY = Utilities.mapRange(progress, topRight.scale.y * size, endSize)
        val bottomLeftSizeX = Utilities.mapRange(progress, bottomLeft.scale.x * size, endSize)
        val bottomLeftSizeY = Utilities.mapRange(progress, bottomLeft.scale.y * size, endSize)
        val bottomRightSizeX = Utilities.mapRange(progress, bottomRight.scale.x * size, endSize)
        val bottomRightSizeY = Utilities.mapRange(progress, bottomRight.scale.y * size, endSize)

        // Start from the bottom right corner
        path.moveTo(right, bottom - bottomRightSizeY)
        bottomRight.shape.addCorner(
            path, IconCornerShape.Position.BottomRight,
            tmpPoint.apply {
                x = bottomRightSizeX
                y = bottomRightSizeY
            },
            progress,
            right - bottomRightSizeX,
            bottom - bottomRightSizeY
        )

        // Move to bottom left
        addLine(
            path,
            right - bottomRightSizeX, bottom,
            left + bottomLeftSizeX, bottom
        )
        bottomLeft.shape.addCorner(
            path, IconCornerShape.Position.BottomLeft,
            tmpPoint.apply {
                x = bottomLeftSizeX
                y = bottomLeftSizeY
            },
            progress,
            left,
            bottom - bottomLeftSizeY
        )

        // Move to top left
        addLine(
            path,
            left, bottom - bottomLeftSizeY,
            left, top + topLeftSizeY
        )
        topLeft.shape.addCorner(
            path, IconCornerShape.Position.TopLeft,
            tmpPoint.apply {
                x = topLeftSizeX
                y = topLeftSizeY
            },
            progress,
            left,
            top
        )

        // And then finally top right
        addLine(
            path,
            left + topLeftSizeX, top,
            right - topRightSizeX, top
        )
        topRight.shape.addCorner(
            path, IconCornerShape.Position.TopRight,
            tmpPoint.apply {
                x = topRightSizeX
                y = topRightSizeY
            },
            progress,
            right - topRightSizeX,
            top
        )

        path.close()
    }

    private fun addLine(path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (x1 == x2 && y1 == y2) return
        path.lineTo(x2, y2)
    }

    override fun toString(): String {
        return "v1|$topLeft|$topRight|$bottomLeft|$bottomRight"
    }

    open fun getHashString() = toString()

    data class Corner(val shape: IconCornerShape, val scale: PointF) {
        constructor(shape: IconCornerShape, scale: Float) : this(shape, PointF(scale, scale))

        override fun toString(): String {
            return "$shape,${scale.x},${scale.y}"
        }

        companion object {
            val fullArc = Corner(IconCornerShape.arc, 1f)
            fun fromString(value: String): Corner {
                val parts = value.split(",")
                val scaleX = parts[1].toFloat()
                val scaleY = if (parts.size >= 3) parts[2].toFloat() else scaleX
                if (scaleX !in 0f..1f) error("scaleX must be in [0, 1]")
                if (scaleY !in 0f..1f) error("scaleY must be in [0, 1]")
                return Corner(IconCornerShape.fromString(parts[0]), PointF(scaleX, scaleY))
            }
        }
    }

    object Circle : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        1f, 1f, 1f, 1f
    ) {

        override fun addShape(path: Path, x: Float, y: Float, radius: Float) {
            path.addCircle(x + radius, y + radius, radius, Path.Direction.CW)
        }

        override fun toString() = "circle"
    }

    object Square : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        .16f, .16f, .16f, .16f
    ) {

        override val qsbEdgeRadius = R.dimen.qsb_radius_square
        override val windowTransitionRadius = .16f

        override fun toString() = "square"
    }

    object RoundedSquare : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        .6f, .6f, .6f, .6f
    ) {

        override val qsbEdgeRadius = R.dimen.qsb_radius_square
        override val windowTransitionRadius = .6f

        override fun toString() = "rounded"
    }

    object Squircle : IconShape(
        IconCornerShape.squircle,
        IconCornerShape.squircle,
        IconCornerShape.squircle,
        IconCornerShape.squircle,
        1f, 1f, 1f, 1f
    ) {

        override val qsbEdgeRadius = R.dimen.qsb_radius_squircle

        override fun toString() = "squircle"
    }

    object Sammy : IconShape(
        IconCornerShape.sammy,
        IconCornerShape.sammy,
        IconCornerShape.sammy,
        IconCornerShape.sammy,
        1f, 1f, 1f, 1f
    ) {

        override val qsbEdgeRadius = R.dimen.qsb_radius_squircle

        override fun toString() = "sammy"
    }

    object Teardrop : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        1f, 1f, 1f, .3f
    ) {

        override fun toString() = "teardrop"
    }

    object Cylinder : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        PointF(1f, .6f),
        PointF(1f, .6f),
        PointF(1f, .6f),
        PointF(1f, .6f)
    ) {

        override fun toString() = "cylinder"
    }

    object Cupertino : IconShape(
        IconCornerShape.cupertino,
        IconCornerShape.cupertino,
        IconCornerShape.cupertino,
        IconCornerShape.cupertino,
        1f, 1f, 1f, 1f
    ) {

        override val windowTransitionRadius = .45f

        override fun toString(): String {
            return "cupertino"
        }
    }

    companion object {

        fun fromString(value: String): IconShape? {
            return when (value) {
                "system" -> IconShapeManager.INSTANCE.noCreate.systemIconShape
                "circle" -> Circle
                "square" -> Square
                "rounded" -> RoundedSquare
                "squircle" -> Squircle
                "sammy" -> Sammy
                "teardrop" -> Teardrop
                "cylinder" -> Cylinder
                "cupertino" -> Cupertino
                "" -> null
                else -> try {
                    parseCustomShape(value)
                } catch (ex: Exception) {
                    null
                }
            }
        }

        private fun parseCustomShape(value: String): IconShape {
            val parts = value.split("|")
            if (parts[0] != "v1") error("unknown config format")
            if (parts.size != 5) error("invalid arguments size")
            return IconShape(
                Corner.fromString(parts[1]),
                Corner.fromString(parts[2]),
                Corner.fromString(parts[3]),
                Corner.fromString(parts[4])
            )
        }
    }
}
