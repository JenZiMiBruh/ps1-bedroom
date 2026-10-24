package com.ps1bedroom.room

import com.ps1bedroom.room.engine.Object3D
import com.ps1bedroom.room.engine.Vec3
import java.util.PriorityQueue
import kotlin.math.*

/**
 * 2D navigation grid and A* pathfinder for Jasper.
 * Routes Jasper safely around the bedroom furniture (bed, heater, desk, TV cart, chair, dresser, bowls)
 * preventing him from getting stuck or oscillating against obstacle boundaries.
 */
class JasperPathfinder {
    val minX = -3.40f
    val maxX = 3.60f
    val minZ = -2.70f
    val maxZ = 3.20f
    val cellSize = 0.10f
    val cols = ceil((maxX - minX) / cellSize).toInt()
    val rows = ceil((maxZ - minZ) / cellSize).toInt()

    private val walkable = BooleanArray(cols * rows)
    private val gScore = FloatArray(cols * rows)
    private val fScore = FloatArray(cols * rows)
    private val parent = IntArray(cols * rows)
    private val closed = BooleanArray(cols * rows)

    fun toGridX(x: Float): Int = ((x - minX) / cellSize).toInt().coerceIn(0, cols - 1)
    fun toGridZ(z: Float): Int = ((z - minZ) / cellSize).toInt().coerceIn(0, rows - 1)
    fun toWorldX(gx: Int): Float = minX + (gx + 0.5f) * cellSize
    fun toWorldZ(gz: Int): Float = minZ + (gz + 0.5f) * cellSize

    /**
     * Rebuilds the walkable grid, padding obstacles by dog radius clearance.
     */
    fun buildGrid(obstacles: List<Pair<Object3D, Float>>) {
        walkable.fill(true)
        for (z in 0 until rows) {
            for (x in 0 until cols) {
                val wx = toWorldX(x)
                val wz = toWorldZ(z)
                val idx = z * cols + x

                // Room perimeter bounds padding
                if (wx < -3.25f || wx > 3.55f || wz < -2.55f || wz > 3.10f) {
                    walkable[idx] = false
                    continue
                }
                // Bed obstacle (with clearance): bed is x in 1.45..3.8, z in -2.9..1.55
                if (wx >= 1.35f && wz <= 1.58f) {
                    walkable[idx] = false
                    continue
                }
                // Nightstand: x in 0.75..1.55, z <= -2.15
                if (wx in 0.70f..1.55f && wz <= -2.10f) {
                    walkable[idx] = false
                    continue
                }
                // Desk: x <= -0.15, z <= -0.55
                if (wx <= -0.10f && wz <= -0.45f) {
                    walkable[idx] = false
                    continue
                }
                // Dresser: x <= -2.85, z in 1.55..3.0
                if (wx <= -2.75f && wz in 1.45f..3.05f) {
                    walkable[idx] = false
                    continue
                }
                // Bowls physical block: mat & bowls (1.50..2.14, 2.22..2.50) so dog doesn't step on bowls
                if (wx in 1.50f..2.14f && wz in 2.22f..2.50f) {
                    walkable[idx] = false
                    continue
                }
                // Dynamic obstacles (heater, chair, tv cart)
                for ((obj, radius) in obstacles) {
                    val odx = wx - obj.position.x
                    val odz = wz - obj.position.z
                    val r = radius + 0.02f // slight clearance padding
                    if (odx * odx + odz * odz <= r * r) {
                        walkable[idx] = false
                        break
                    }
                }
            }
        }
    }

    /**
     * Finds the nearest walkable cell index to (gx, gz) if the cell itself is blocked.
     */
    fun findNearestWalkable(gx: Int, gz: Int): Int {
        val idx = gz * cols + gx
        if (walkable[idx]) return idx
        var best = -1
        var bestD2 = Float.MAX_VALUE
        val maxR = 15
        for (r in 1..maxR) {
            for (dz in -r..r) {
                for (dx in -r..r) {
                    if (abs(dx) != r && abs(dz) != r) continue
                    val nx = gx + dx
                    val nz = gz + dz
                    if (nx in 0 until cols && nz in 0 until rows) {
                        val nidx = nz * cols + nx
                        if (walkable[nidx]) {
                            val d2 = (dx * dx + dz * dz).toFloat()
                            if (d2 < bestD2) {
                                bestD2 = d2
                                best = nidx
                            }
                        }
                    }
                }
            }
            if (best != -1) return best
        }
        return idx
    }

    /**
     * Tests if there is a collision-free straight line of sight between two 2D points.
     */
    fun hasLineOfSight(x1: Float, z1: Float, x2: Float, z2: Float): Boolean {
        val dx = x2 - x1
        val dz = z2 - z1
        val dist = hypot(dx, dz)
        if (dist < 0.05f) return true
        val step = cellSize * 0.4f
        val count = ceil(dist / step).toInt().coerceAtLeast(2)
        for (i in 1 until count) {
            val t = i.toFloat() / count
            val gx = toGridX(x1 + dx * t)
            val gz = toGridZ(z1 + dz * t)
            if (!walkable[gz * cols + gx]) return false
        }
        return true
    }

    /**
     * Computes a smoothed A* path of waypoints from start to goal avoiding all obstacles.
     */
    fun findPath(start: Vec3, goal: Vec3, obstacles: List<Pair<Object3D, Float>>): List<Vec3> {
        buildGrid(obstacles)

        val sIdx = findNearestWalkable(toGridX(start.x), toGridZ(start.z))
        val gIdx = findNearestWalkable(toGridX(goal.x), toGridZ(goal.z))
        if (sIdx == gIdx) {
            val dist = hypot(goal.x - start.x, goal.z - start.z)
            return if (dist > 0.06f) listOf(Vec3(goal.x, 0f, goal.z)) else emptyList()
        }

        gScore.fill(Float.MAX_VALUE)
        fScore.fill(Float.MAX_VALUE)
        parent.fill(-1)
        closed.fill(false)

        val targetGx = gIdx % cols
        val targetGz = gIdx / cols
        gScore[sIdx] = 0f
        fScore[sIdx] = hypot(toWorldX(sIdx % cols) - toWorldX(targetGx), toWorldZ(sIdx / cols) - toWorldZ(targetGz))

        val pq = PriorityQueue<Int>(Comparator { a, b -> fScore[a].compareTo(fScore[b]) })
        pq.add(sIdx)

        val dx = intArrayOf( 0,  0, -1,  1, -1,  1, -1,  1)
        val dz = intArrayOf(-1,  1,  0,  0, -1, -1,  1,  1)
        val cost = floatArrayOf(1f, 1f, 1f, 1f, 1.4142f, 1.4142f, 1.4142f, 1.4142f)

        var bestNode = sIdx
        var bestHeuristic = fScore[sIdx]

        while (!pq.isEmpty()) {
            val curr = pq.poll()
            if (curr == gIdx) {
                bestNode = curr
                break
            }
            if (closed[curr]) continue
            closed[curr] = true

            val cx = curr % cols
            val cz = curr / cols

            for (i in 0 until 8) {
                val nx = cx + dx[i]
                val nz = cz + dz[i]
                if (nx < 0 || nx >= cols || nz < 0 || nz >= rows) continue
                val nIdx = nz * cols + nx
                if (!walkable[nIdx] || closed[nIdx]) continue

                // Prevent cutting through corners diagonally
                if (i >= 4) {
                    if (!walkable[cz * cols + nx] || !walkable[nz * cols + cx]) continue
                }

                val tentativeG = gScore[curr] + cost[i] * cellSize
                if (tentativeG < gScore[nIdx]) {
                    parent[nIdx] = curr
                    gScore[nIdx] = tentativeG
                    val h = hypot(toWorldX(nx) - toWorldX(targetGx), toWorldZ(nz) - toWorldZ(targetGz))
                    fScore[nIdx] = tentativeG + h
                    if (h < bestHeuristic) {
                        bestHeuristic = h
                        bestNode = nIdx
                    }
                    pq.add(nIdx)
                }
            }
        }

        // Reconstruct raw path
        val raw = ArrayList<Vec3>()
        var curr: Int = bestNode
        while (curr != -1) {
            raw.add(Vec3(toWorldX(curr % cols), 0f, toWorldZ(curr / cols)))
            curr = parent[curr]
        }
        raw.reverse()

        // Path smoothing via line-of-sight raycasting
        val pts = ArrayList<Vec3>(raw.size + 2)
        pts.add(Vec3(start.x, 0f, start.z))
        for (i in 1 until raw.size - 1) pts.add(raw[i])
        pts.add(Vec3(goal.x, 0f, goal.z))

        val smoothed = ArrayList<Vec3>()
        var cIdx = 0
        while (cIdx < pts.size - 1) {
            var furthest = pts.size - 1
            while (furthest > cIdx + 1) {
                if (hasLineOfSight(pts[cIdx].x, pts[cIdx].z, pts[furthest].x, pts[furthest].z)) break
                furthest--
            }
            smoothed.add(pts[furthest])
            cIdx = furthest
        }
        return smoothed
    }
}
