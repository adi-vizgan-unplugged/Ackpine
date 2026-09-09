/*
 * Copyright (C) 2026 Ilya Fomichev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.solrudev.ackpine.impl.database.dao

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.solrudev.ackpine.impl.HasAckpineDatabaseTest
import ru.solrudev.ackpine.impl.database.model.SessionEntity
import ru.solrudev.ackpine.impl.installer.getApks
import ru.solrudev.ackpine.impl.installer.getV4Signatures
import ru.solrudev.ackpine.impl.testutil.createInstallSessionEntity
import ru.solrudev.ackpine.installer.parameters.InstallerType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class InstallSessionDaoTest : HasAckpineDatabaseTest() {

	@Test
	fun installUrisWithoutV4SignaturesRoundTrip() {
		val id = UUID.randomUUID().toString()
		database.installSessionDao().insertInstallSession(
			createInstallSessionEntity(
				id = id,
				state = SessionEntity.State.PENDING,
				installerType = InstallerType.SESSION_BASED,
				uris = listOf("file:///base.apk")
			)
		)

		val restored = assertNotNull(database.installSessionDao().getInstallSession(id))

		assertEquals(listOf("file:///base.apk"), restored.uris.map { it.uri })
		assertEquals(listOf(null), restored.uris.map { it.v4SignatureUri })
		assertEquals(emptyMap(), restored.getV4Signatures())
	}

	@Test
	fun v4SignatureUrisRoundTripPairedWithTheirApks() {
		val id = UUID.randomUUID().toString()
		database.installSessionDao().insertInstallSession(
			createInstallSessionEntity(
				id = id,
				state = SessionEntity.State.PENDING,
				installerType = InstallerType.SESSION_BASED,
				uris = listOf("file:///base.apk", "file:///split1.apk", "file:///split2.apk"),
				v4SignatureUris = mapOf(
					"file:///base.apk" to "file:///base.apk.idsig",
					"file:///split2.apk" to "file:///split2.apk.idsig"
				)
			)
		)

		val restored = assertNotNull(database.installSessionDao().getInstallSession(id))

		// The pairing is row-local, so it survives regardless of the order rows come back in.
		assertEquals(
			mapOf(
				"file:///base.apk" to "file:///base.apk.idsig",
				"file:///split1.apk" to null,
				"file:///split2.apk" to "file:///split2.apk.idsig"
			),
			restored.uris.associate { it.uri to it.v4SignatureUri }
		)
		assertEquals(3, restored.getApks().size)
		assertEquals(2, restored.getV4Signatures().size)
	}
}
