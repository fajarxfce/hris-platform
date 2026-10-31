/** Owned private demo only. All writes use normal authorization and retained idempotency. */
import { createRequire } from "node:module";
import { readFileSync, writeFileSync, existsSync, renameSync } from "node:fs";
import { randomUUID, createHash } from "node:crypto";
const { request } = createRequire(
	new URL("../apps/dashboard/package.json", import.meta.url),
)("@playwright/test");
process.umask(0o077);
const [configFile, stateFile, mode] = process.argv.slice(2);
if (!configFile || !stateFile || !["--inspect", "--apply"].includes(mode)) {
	throw new Error(
		"Usage: node tool/seed_mobile_demo.mjs PRIVATE_CONFIG PRIVATE_STATE --inspect|--apply",
	);
}
const config = JSON.parse(readFileSync(configFile, "utf8"));
if (config.asOf !== "2026-10-31")
	throw new Error("This dated example requires the 31 October 2026 manifest");
const target = {
	company: config.company,
	account: config.account,
	employment: config.employment,
	asOf: config.asOf,
};
const state = existsSync(stateFile)
	? JSON.parse(readFileSync(stateFile, "utf8"))
	: { target, ids: {}, commands: {} };
if (JSON.stringify(state.target) !== JSON.stringify(target))
	throw new Error("Existing manifest belongs to a different target");
const save = () => {
	writeFileSync(stateFile + ".tmp", JSON.stringify(state, null, 2) + "\n", {
		mode: 0o600,
	});
	renameSync(stateFile + ".tmp", stateFile);
};
const id = (key) => {
	if (!state.ids[key]) {
		state.ids[key] = randomUUID();
		save();
	}
	return state.ids[key];
};
const admin = await request.newContext({
	baseURL: config.baseUrl,
	storageState: config.adminStorageState,
	timeout: 15000,
});
const visitor = await request.newContext({
	baseURL: config.baseUrl,
	timeout: 15000,
});
let employee, credentials;
const base = "/api/v1/companies/" + config.company;
async function checked(response, label) {
	if (response.status() !== 200) {
		let code;
		try {
			code = (await response.json()).code;
		} catch {}
		throw new Error(
			label + " HTTP " + response.status() + (code ? " " + code : ""),
		);
	}
	return response.json();
}
const get = async (client, path) => checked(await client.get(path), "Read");
async function command(client, key, method, path, body, extraHeaders = {}) {
	if (mode !== "--apply") throw new Error("Mutation requires --apply");
	if (state.commands[key]?.done) return state.commands[key].result;
	if (!state.commands[key]) {
		state.commands[key] = {
			operation: randomUUID(),
			method,
			path,
			body,
			extraHeaders,
		};
		save();
	}
	const frozen = state.commands[key];
	if (frozen.method !== method || frozen.path !== path)
		throw new Error("Retained command target changed");
	const headers = {
		"Idempotency-Key": frozen.operation,
		...frozen.extraHeaders,
	};
	if (client === admin) {
		const csrf = await get(admin, "/api/v1/auth/csrf");
		headers[csrf.headerName] = csrf.token;
	}
	const result = await checked(
		await client[method](path, {
			headers,
			data: frozen.body?.bytesBase64
				? Buffer.from(frozen.body.bytesBase64, "base64")
				: frozen.body,
		}),
		key,
	);
	frozen.done = true;
	frozen.result = result;
	save();
	console.log(JSON.stringify({ command: key, complete: true }));
	return result;
}
try {
	const company = await get(admin, base);
	if (company.code !== "HRISDEMO")
		throw new Error(
			"This seeder requires the owned HRISDEMO fictional company",
		);
	const login = await checked(
		await visitor.post("/api/v1/auth/native/login", {
			headers: { "Idempotency-Key": randomUUID() },
			data: {
				email: config.email,
				password: config.password,
				deviceName: "Fictional sample data setup",
			},
		}),
		"Owned employee login",
	);
	credentials = login.credentials;
	if (!credentials)
		throw new Error("Expected an already admitted employee session");
	employee = await request.newContext({
		baseURL: config.baseUrl,
		timeout: 15000,
		extraHTTPHeaders: {
			Authorization: "Bearer " + credentials.accessToken,
			"X-HRIS-Client-Platform": "ANDROID",
			"X-HRIS-Client-Build": "2",
		},
	});
	const owned = await get(employee, base + "/me/employments");
	if (
		owned.employments.items.length !== 1 ||
		owned.employments.items[0].id !== config.employment
	)
		throw new Error("Owned employment mismatch");
	const account = await get(
		admin,
		"/api/v1/identity/accounts/" + config.account,
	);
	const person = owned.employments.items[0].person;
	if (
		account.account.id !== config.account ||
		person.id !== config.person ||
		person.accountId !== config.account
	)
		throw new Error("Existing account/person binding mismatch");
	const balance = await get(
		admin,
		base +
			"/leave/employees/" +
			config.employment +
			"/balances/" +
			config.leaveType +
			"/2026",
	);
	console.log(
		JSON.stringify({
			verifiedFictionalCompany: true,
			ownedEmployment: true,
			bindingPreserved: true,
			mode,
			retainedCommands: Object.keys(state.commands).length,
		}),
	);
	if (mode === "--apply") {
		for (const [index, name] of [
			"Rani Prasetyo (Demo)",
			"Bima Santoso (Demo)",
			"Nadia Putri (Demo)",
			"Dimas Saputra (Demo)",
		].entries()) {
			await command(admin, "colleague" + index, "post", base + "/employees", {
				id: id("colleague" + index),
				employeeNumber: "SAMPLE-" + String(index + 1).padStart(3, "0"),
				person: {
					id: id("person" + index),
					legalName: name,
					nationality: "ID",
				},
				terms: {
					effectiveFrom: "2026-10-01",
					startDate: "2026-10-01",
					status: "ACTIVE",
					contract: "PERMANENT",
				},
				reason:
					"Fictional sample for mobile directory layout review; no login account",
			});
		}
		await command(
			admin,
			"sampleLeaveBalanceV2",
			"post",
			base +
				"/leave/employees/" +
				config.employment +
				"/balances/" +
				config.leaveType +
				"/2026/adjustments",
			{
				days: "10",
				expectedVersion: balance.balance.version,
				reason:
					"Ten additional fictional days for requested mobile sample data; not a statutory entitlement",
			},
		);
		for (const [index, date] of [
			"2026-11-02",
			"2026-11-04",
			"2026-11-06",
		].entries()) {
			const path = base + "/leave/requests/" + id("leave" + index);
			await command(
				employee,
				"leave" + index,
				"post",
				base + "/leave/requests",
				{
					id: id("leave" + index),
					employeeId: config.employment,
					typeId: config.leaveType,
					days: [
						{ workDate: date, portion: index === 1 ? "FIRST_HALF" : "FULL" },
					],
					reason: [
						"Contoh: keperluan keluarga",
						"Contoh: pemeriksaan kesehatan",
						"Contoh: urusan administrasi pribadi",
					][index],
				},
			);
			if (index === 0)
				await command(
					admin,
					"approveLeave" + index,
					"post",
					path + "/decisions",
					{
						version: 0,
						decision: "APPROVE",
						reason: "Persetujuan data contoh untuk tinjauan tampilan mobile",
					},
				);
			if (index === 2)
				await command(
					employee,
					"withdrawLeaveV2" + index,
					"post",
					path + "/withdraw",
					{ version: 0, reason: "Contoh: jadwal kegiatan pribadi berubah" },
				);
		}
		for (const [index, title] of [
			"Contoh: transportasi kunjungan klien",
			"Contoh: perjalanan ke kantor cabang",
			"Contoh: pengiriman dokumen",
		].entries()) {
			const path = base + "/expenses/claims/" + id("expense" + index);
			await command(employee, "expenseDraft" + index, "put", path + "/draft", {
				employmentId: config.employment,
				title,
				description:
					"Data fiktif untuk mencoba alur klaim biaya pada aplikasi HRIS.",
				lines: [
					{
						id: id("expenseLine" + index),
						categoryId: config.expenseCategory,
						occurredOn: ["2026-10-26", "2026-10-27", "2026-10-28"][index],
						amount: ["125000.00", "275000.00", "45000.00"][index],
						description: [
							"Tiket kereta pulang pergi",
							"Transportasi rapat koordinasi",
							"Kurir dokumen kantor",
						][index],
						costCenterId: null,
						receiptRevisionIds: [],
					},
				],
				expectedVersion: null,
				reason: "Pembuatan contoh klaim biaya untuk tinjauan mobile",
			});
			if (index < 2) {
				await command(
					employee,
					"submitExpense" + index,
					"post",
					path + "/submit",
					{
						submissionId: id("expenseSubmission" + index),
						expectedVersion: 0,
						reason: "Contoh pengajuan penggantian biaya",
					},
				);
				if (index === 0)
					await command(
						admin,
						"approveExpense" + index,
						"post",
						base +
							"/expenses/submissions/" +
							id("expenseSubmission" + index) +
							"/decisions",
						{
							expectedVersion: 1,
							expectedApprovalVersion: 0,
							decision: "APPROVE",
							reason: "Persetujuan contoh biaya, tanpa pembayaran nyata",
							acknowledgeDuplicates: false,
						},
					);
			}
		}
		for (const [index, date] of [
			"2026-10-26",
			"2026-10-27",
			"2026-10-28",
		].entries()) {
			const review = await get(
				employee,
				base +
					"/workforce/overtime/planning/employees/" +
					config.employment +
					"?workDate=" +
					date,
			);
			const path = base + "/workforce/overtime/" + id("overtime" + index);
			const interval = {
				startsAt: date + "T11:00:00Z",
				endsAt: date + "T13:00:00Z",
				breakMinutes: 0,
			};
			await command(
				employee,
				"overtime" + index,
				"post",
				base + "/workforce/overtime",
				{
					id: id("overtime" + index),
					employeeId: config.employment,
					expectedEmploymentVersion: review.employee.version,
					workDate: date,
					requested: interval,
					planningRevision: review.revision,
					reason: [
						"Contoh: rekap laporan bulanan",
						"Contoh: persiapan presentasi klien",
						"Contoh: pemeriksaan inventaris",
					][index],
				},
			);
			if (index === 0) {
				await command(
					employee,
					"actualOvertime" + index,
					"post",
					path + "/actual",
					{
						expectedVersion: 0,
						actual: interval,
						reason: "Contoh: rekap laporan selesai",
					},
				);
				await command(
					admin,
					"approveOvertime" + index,
					"post",
					path + "/decisions",
					{
						expectedVersion: 1,
						decision: "APPROVE",
						reason: "Verifikasi fakta lembur fiktif untuk tinjauan mobile",
					},
				);
			}
			if (index === 2)
				await command(
					employee,
					"withdrawOvertime" + index,
					"post",
					path + "/withdraw",
					{
						expectedVersion: 0,
						reason: "Contoh: pekerjaan selesai pada jam kerja",
					},
				);
		}
		const attendance = await get(
			admin,
			base +
				"/workforce/employees/" +
				config.employment +
				"/attendance?from=2026-10-19&until=2026-10-23",
		);
		for (const [index, date] of [
			"2026-10-19",
			"2026-10-20",
			"2026-10-21",
			"2026-10-22",
			"2026-10-23",
		].entries()) {
			const existing = attendance.find((day) => day.workDate === date);
			const key = "attendanceSample" + index;
			if (
				!state.commands[key] &&
				(existing?.correction || existing?.entries.length)
			)
				throw new Error("Sample attendance would replace an existing record");
			await command(
				admin,
				key,
				"post",
				base +
					"/workforce/employees/" +
					config.employment +
					"/attendance/corrections",
				{
					workDate: date,
					clockIn: date + (index === 1 ? "T01:15:00Z" : "T01:00:00Z"),
					clockOut: date + "T10:00:00Z",
					breakMinutes: 60,
					expectedVersion: null,
					reason:
						"Data kehadiran fiktif untuk tinjauan mobile; bukan rekaman perangkat atau GPS",
				},
			);
		}
		await command(
			admin,
			"sampleAudience",
			"put",
			base + "/communications/audience-groups/" + id("sampleAudience"),
			{
				name: "Contoh mobile pribadi",
				active: true,
				employmentIds: [config.employment],
				expectedVersion: null,
				reason: "Batasi data contoh kotak masuk ke karyawan pemilik permintaan",
			},
		);
		const announcements = [
			[
				"Contoh: panduan pengajuan cuti",
				"Pilih jenis cuti dan tanggal, lengkapi alasan, lalu tinjau perhitungan sebelum mengirim. Ini adalah pengumuman contoh untuk mencoba aplikasi HRIS.",
			],
			[
				"Contoh: pengingat bukti pengeluaran",
				"Simpan bukti perjalanan pada Dokumen, kemudian lampirkan ke rincian klaim biaya. Seluruh nominal dan dokumen pada lingkungan ini merupakan data fiktif.",
			],
			[
				"Contoh: agenda koordinasi tim",
				"Senin, 2 November 2026, pukul 09.00 WIB: koordinasi mingguan dan pembagian tugas. Jadwal ini hanya data contoh; tidak ada undangan kalender atau email yang dikirim.",
			],
		];
		for (const [index, [title, body]] of announcements.entries()) {
			const path = base + "/announcements/" + id("announcement" + index);
			await command(admin, "announcementDraft" + index, "put", path, {
				title,
				body,
				audience: { kind: "GROUP", targetIds: [id("sampleAudience")] },
				acknowledgementRequired: index === 0,
				emailNotification: false,
				expectedVersion: null,
				reason: "Contoh kotak masuk atas permintaan pemilik akun",
			});
			if (!state.commands["announcementPublish" + index]?.done) {
				const audience = await get(
					admin,
					path + "/audience-preview?expectedVersion=0",
				);
				if (audience.recipientCount !== 1)
					throw new Error(
						"Sample announcement must target exactly the requested employee",
					);
			}
			await command(
				admin,
				"announcementPublish" + index,
				"post",
				path + "/publish",
				{
					expectedVersion: 0,
					reason: "Terbitkan contoh hanya kepada pemilik akun, tanpa email",
				},
			);
		}
		for (const [index, title] of [
			"Contoh sertifikat pelatihan",
			"Contoh bukti transportasi",
		].entries()) {
			const content =
				"BT /F1 18 Tf 50 760 Td (" +
				title +
				") Tj 0 -40 Td /F1 12 Tf (DATA FIKTIF - hanya untuk mencoba HRIS Mobile.) Tj 0 -24 Td (Tidak berlaku sebagai bukti resmi atau pembayaran nyata.) Tj ET";
			const objects = [
				"<< /Type /Catalog /Pages 2 0 R >>",
				"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
				"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
				"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
				"<< /Length " +
					Buffer.byteLength(content) +
					" >>\nstream\n" +
					content +
					"\nendstream",
			];
			let pdf = "%PDF-1.4\n";
			const offsets = [0];
			for (const [i, obj] of objects.entries()) {
				offsets.push(Buffer.byteLength(pdf));
				pdf += i + 1 + " 0 obj\n" + obj + "\nendobj\n";
			}
			const xref = Buffer.byteLength(pdf);
			pdf +=
				"xref\n0 6\n0000000000 65535 f \n" +
				offsets
					.slice(1)
					.map((x) => String(x).padStart(10, "0") + " 00000 n \n")
					.join("") +
				"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" +
				xref +
				"\n%%EOF\n";
			const bytes = Buffer.from(pdf),
				sha256 = createHash("sha256").update(bytes).digest("hex");
			const path =
				base + "/documents/revisions/" + id("documentRevision" + index);
			await command(
				admin,
				"documentDeclaration" + index,
				"post",
				base + "/documents/uploads",
				{
					documentId: id("document" + index),
					revisionId: id("documentRevision" + index),
					employmentId: config.employment,
					title,
					classification: index === 0 ? "PERSONAL" : "RECEIPT",
					expectedDocumentVersion: 0,
					fileName:
						index === 0
							? "contoh-sertifikat.pdf"
							: "contoh-bukti-transportasi.pdf",
					mediaType: "application/pdf",
					size: bytes.length,
					sha256,
					metadata: {
						category: index === 0 ? "CERTIFICATE" : "RECEIPT",
						identityNumber: "SAMPLE-2026-" + (index + 1),
						issuingCountry: "ID",
						issuedOn: "2026-10-01",
						validUntil: index === 0 ? "2027-10-01" : null,
					},
					reason: "Dokumen fiktif untuk tinjauan layout dan pemilihan lampiran",
				},
			);
			await command(
				admin,
				"documentBytes" + index,
				"post",
				path + "/chunks",
				{ bytesBase64: bytes.toString("base64") },
				{
					"Content-Type": "application/octet-stream",
					"Upload-Offset": "0",
					"Upload-Checksum-Sha256": sha256,
				},
			);
			const current = await get(admin, path);
			await command(
				admin,
				"documentValidation" + index,
				"post",
				path + "/validate",
				{
					expectedVersion: current.version,
					reason: "Periksa dokumen contoh melalui scanner yang aktif",
				},
			);
			let inspected;
			const deadline = Date.now() + 90000;
			while (Date.now() < deadline) {
				inspected = await get(admin, path);
				if (inspected.status === "READY") break;
				if (["REJECTED", "EXPIRED", "CANCELLED"].includes(inspected.status))
					throw new Error("Sample document inspection " + inspected.status);
				await new Promise((resolve) => setTimeout(resolve, 750));
			}
			if (inspected?.status !== "READY")
				throw new Error("Sample document inspection deadline exceeded");
			const download = await employee.get(path + "/content");
			if (download.status() !== 200 || !(await download.body()).equals(bytes))
				throw new Error("Owned document download mismatch");
		}
		state.summary = {
			completedAt: new Date().toISOString(),
			colleagues: 4,
			leaveRequests: 3,
			expenseClaims: 3,
			overtimeRequests: 3,
			attendanceDays: 5,
			announcements: 3,
			documents: 2,
			commands: Object.keys(state.commands).length,
		};
		save();
		console.log(JSON.stringify(state.summary));
	}
	if (state.summary) {
		const statuses = { leave: [], expenses: [], overtime: [] };
		for (let index = 0; index < 3; index++) {
			statuses.leave.push(
				(
					await get(
						employee,
						base + "/leave/requests/" + state.ids["leave" + index],
					)
				).status,
			);
			statuses.expenses.push(
				(
					await get(
						employee,
						base +
							"/expenses/claims/" +
							state.ids["expense" + index] +
							"?scope=SELF",
					)
				).status,
			);
			statuses.overtime.push(
				(
					await get(
						employee,
						base + "/workforce/overtime/" + state.ids["overtime" + index],
					)
				).request.status,
			);
		}
		const attendance = await get(
			employee,
			base +
				"/workforce/employees/" +
				config.employment +
				"/attendance?from=2026-10-19&until=2026-10-23",
		);
		const inbox = await get(employee, base + "/inbox?limit=50");
		const announcements = new Set(
			[0, 1, 2].map((i) => state.ids["announcement" + i]),
		);
		const inboxCount = inbox.items.filter((row) =>
			announcements.has(row.announcementId),
		).length;
		if (
			JSON.stringify(statuses) !==
			JSON.stringify({
				leave: ["APPROVED", "PENDING", "CANCELLED"],
				expenses: ["APPROVED", "PENDING", "DRAFT"],
				overtime: ["APPROVED", "PLANNED", "WITHDRAWN"],
			})
		)
			throw new Error("Sample request state verification failed");
		if (
			attendance.filter((day) => day.correction).length !== 5 ||
			inboxCount !== 3
		)
			throw new Error("Sample attendance/inbox read verification failed");
		state.verification = {
			verifiedAt: new Date().toISOString(),
			statuses,
			attendanceDays: 5,
			inboxItems: 3,
			readAsOwnedEmployee: true,
		};
		save();
		console.log(JSON.stringify(state.verification));
	}
} catch (error) {
	console.error(error.message?.split("\n")[0] ?? "Sample setup failed");
	process.exitCode = 1;
} finally {
	if (credentials) {
		const response = await visitor.delete(
			"/api/v1/auth/native/sessions/" + credentials.sessionId,
			{ headers: { Authorization: "Bearer " + credentials.accessToken } },
		);
		if (![200, 204].includes(response.status())) {
			console.error("Owned setup session cleanup HTTP " + response.status());
			process.exitCode = 1;
		}
	}
	state.setupSessionRevoked = !!credentials && process.exitCode !== 1;
	if (existsSync(stateFile)) save();
	await employee?.dispose();
	await visitor.dispose();
	await admin.dispose();
}
