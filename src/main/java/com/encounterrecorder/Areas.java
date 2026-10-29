/*
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * Copyright (c) 2018, PandahRS <https://github.com/PandahRS>
 * Copyright (c) 2020, Brooklyn <https://github.com/Broooklyn>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.encounterrecorder;

import java.util.HashMap;
import java.util.Map;

/**
 * Place names by map square (region), for naming a recording that wasn't given a name. RuneLite has no
 * API for place names; this list comes from RuneLite's Discord plugin (DiscordGameEventType, BSD-2, its
 * copyright kept above), with places it lacks added from recordings.
 */
final class Areas
{
	/** Where a map square is in more than one place, the first listed wins: raids and bosses before regions */
	private static final Map<Integer, String> BY_REGION = new HashMap<>();
	/**
	 * Instances whose map square the open world shares under another name: Scurrius's room is copied from
	 * the Varrock Sewers
	 */
	private static final Map<Integer, String> INSTANCES = Map.of(13210, "Scurrius");

	static
	{
		// Raids
		// A room of the Fractured Archive RuneLite's list misses, found in a recording
		area("The Fractured Archive", 16471);
		area("Chambers of Xeric", 12889, 13136, 13137, 13138, 13139, 13140, 13141, 13145, 13393, 13394, 13395, 13396, 13397, 13401);
		area("Theatre of Blood", 12611, 12612, 12613, 12867, 12869, 13122, 13123, 13125, 13379);
		area("Tombs of Amascut", 14160, 14162, 14164, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700);
		area("The Fractured Archive", 16214, 16726, 16215, 16727, 16216, 16728, 16472, 16474);
		area("Jaltevas Pyramid", 13454);
		area("Osmumten's Burial Chamber", 14672);
		// Bosses
		area("Abyssal Sire", 11851, 11850, 12363, 12362);
		area("Araxxor", 14489);
		area("Cerberus", 4883, 5140, 5395);
		area("Commander Zilyana", 11602);
		area("Corporeal Beast", 11842, 11844);
		area("Dagannoth Kings", 11588, 11589);
		area("Duke Sucellus", 12132);
		area("General Graardor", 11347);
		area("Giant Mole", 6993, 6992);
		area("Grotesque Guardians", 6727);
		area("Hespori", 5021);
		area("Alchemical Hydra", 5536);
		area("Kalphite Queen", 13972);
		area("Kraken", 9116);
		area("Kree'arra", 11346);
		area("K'ril Tsutsaroth", 11603);
		area("Nex", 11601);
		area("Nightmare of Ashihama", 15515);
		area("Phantom Muspah", 11330);
		area("Sarachnis", 7322);
		area("Skotizo", 9048);
		area("Thermonuclear smoke devil", 9363, 9619);
		area("Tempoross", 12076);
		area("The Leviathan", 8291);
		area("The Royal Titans", 11669);
		area("The Whisperer", 10595);
		area("Vardorvis", 4405);
		area("Vorkath", 9023);
		area("Wintertodt", 6462);
		area("Yama", 6045);
		area("Zalcano", 12126);
		area("Zulrah", 9007);
		// Minigames
		area("Ardougne Rat Pits", 10646);
		area("Barbarian Assault", 7508, 7509, 10322);
		area("Barrows", 14131, 14231);
		area("Blast Furnace", 7757);
		area("Brimhaven Agility Arena", 11157);
		area("Burthorpe Games Room", 8781);
		area("Castle Wars", 9520, 9620);
		area("Clan Wars", 12621, 12622, 12623, 13130, 13131, 13133, 13134, 13135, 13386, 13387, 13390, 13641, 13642, 13643, 13644, 13645, 13646, 13647, 13899, 13900, 14155, 14156);
		area("Emir's Arena", 13362, 13363);
		area("Fishing Trawler", 7499);
		area("Fortis Colosseum", 7216);
		area("Fortis Colosseum Lobby", 7316);
		area("The Gauntlet", 12127, 7512);
		area("Corrupted Gauntlet", 7768);
		area("Giants' Foundry", 13491);
		area("Guardians of the Rift", 14484);
		area("Hallowed Sepulchre", 8797, 9051, 9052, 9053, 9054, 9309, 9563, 9565, 9821, 10074, 10075, 10077);
		area("The Inferno", 9043);
		area("Keldagrim Rat Pits", 7753);
		area("LMS - Deserted Island", 13658, 13659, 13660, 13914, 13915, 13916);
		area("LMS - Wild Varrock", 13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432);
		area("Mage Training Arena", 13462, 13463);
		area("Nightmare Zone", 9033);
		area("Pest Control", 10536);
		area("Port Sarim Rat Pits", 11926);
		area("Pyramid Plunder", 7749);
		area("Rogues' Den", 11854, 11855, 12109, 12110, 12111);
		area("Sorceress's Garden", 11605);
		area("Soul Wars", 8493, 8748, 8749, 9005);
		area("Temple Trekking", 8014, 8270, 8256, 8782, 9038, 9294, 9550, 9806);
		area("Tithe Farm", 7222);
		area("Trouble Brewing", 15150);
		area("Tzhaar Fight Caves", 9551);
		area("Tzhaar Fight Pits", 9552);
		area("Varrock Rat Pits", 11599);
		area("Volcanic Mine", 15263, 15262);
		// Dungeons
		area("Abandoned Mine", 13618, 13718, 11079, 11078, 11077, 10823, 10822, 10821);
		area("Ah Za Rhoon", 11666);
		area("Ancient Cavern", 6483, 6995);
		area("Ape Atoll Dungeon", 11150, 10894);
		area("Ape Atoll Banana Plantation", 10895);
		area("West Ardougne Basement", 10135);
		area("Ardougne Sewers", 10134, 10136, 10391, 10647);
		area("Asgarnian Ice Caves", 11925, 12181);
		area("Tomb of Bervirius", 11154);
		area("Brimhaven Dungeon", 10901, 10900, 10899, 10645, 10644, 10643);
		area("Brine Rat Cavern", 10910);
		area("Catacombs of Kourend", 6557, 6556, 6813, 6812);
		area("Champions' Challenge", 12696);
		area("Chaos Druid Tower", 10392);
		area("Chasm of Fire", 5789);
		area("Chasm of Tears", 12948);
		area("Chinchompa Hunting Ground", 10129);
		area("Civitas illa Fortis Underground", 6549, 6804, 6805);
		area("Clock Tower Basement", 10390);
		area("Corsair Cove Dungeon", 8076, 8332);
		area("Crabclaw Caves", 6553, 6809);
		area("Crandor Dungeon", 11414);
		area("Crash Site Cavern", 8280, 8536);
		area("Crumbling Tower", 7827);
		area("Daeyalt Essence Mine", 14744);
		area("Digsite Dungeon", 13464, 13465);
		area("Dorgesh-Kaan South Dungeon", 10833);
		area("Dorgeshuun Mines", 12950, 13206);
		area("Draynor Sewers", 12439, 12438);
		area("Dwarven Mines", 12185, 12184, 12183);
		area("Eagles' Peak Dungeon", 8013);
		area("Ectofuntus", 14746);
		area("Edgeville Dungeon", 12441, 12442, 12443, 12698);
		area("Elemental Workshop", 10906, 7760);
		area("Elven rabbit cave", 13252);
		area("Enakhra's Temple", 12423);
		area("Evil Chicken's Lair", 9796);
		area("Experiment Cave", 14235, 13979);
		area("Ferox Enclave Dungeon", 12700);
		area("Forthos Dungeon", 7323);
		area("Fremennik Slayer Dungeon", 10907, 10908, 11164);
		area("Glarial's Tomb", 10137);
		area("Goblin Cave", 10393);
		area("Grand Tree Tunnels", 9882);
		area("H.A.M. Hideout", 12694);
		area("H.A.M. Store room", 10321);
		area("Heroes' Guild Mine", 11674);
		area("Iorwerth Dungeon", 12737, 12738, 12993, 12994);
		area("Isle of Souls Dungeon", 8593);
		area("Jatizso Mines", 9631);
		area("Jiggig Burial Tomb", 9875, 9874);
		area("Jogre Dungeon", 11412);
		area("Karamja Dungeon", 11413);
		area("Karuulm Slayer Dungeon", 5280, 5279, 5023, 5535, 5022, 4766, 4510, 4511, 4767, 4768, 4512);
		area("KGP Headquarters", 10658);
		area("Kruk's Dungeon", 9358, 9359, 9360, 9615, 9616, 9871, 10125, 10126, 10127, 10128, 10381, 10382, 10383, 10384, 10637, 10638, 10639, 10640);
		area("Legends' Guild Dungeon", 10904);
		area("Lighthouse", 10140);
		area("Lizardman Caves", 5275);
		area("Lizardman Temple", 5277);
		area("Lumbridge Swamp Caves", 12693, 12949);
		area("Lunar Isle Mine", 9377);
		area("Maniacal Monkey Hunter Area", 11662);
		area("Meiyerditch Mine", 9544);
		area("Miscellania Dungeon", 10144, 10400);
		area("Mogre Camp", 11924);
		area("Mos Le'Harmless Caves", 14994, 14995, 15251);
		area("Motherlode Mine", 14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193);
		area("Mourner Tunnels", 7752, 8008);
		area("Mouse Hole", 9046);
		area("Meiyerditch Laboratories", 14232, 14233, 14487, 14488);
		area("Myreque Hideout", 13721, 13974, 13977, 13978);
		area("Myths' Guild Dungeon", 7564, 7820, 7821);
		area("Observatory Dungeon", 9362);
		area("Ogre Enclave", 10387);
		area("Ourania Cave", 12119);
		area("Quidamortem Cave", 4763);
		area("Rashiliyta's Tomb", 11668);
		area("Ruins of Camdozaal", 11609, 11610, 11611, 11865, 11866, 11867, 12121, 12122, 12123);
		area("Salt Mine", 11425);
		area("Saradomin Shrine (Paterdomus)", 13722);
		area("Shade Catacombs", 13975);
		area("Shadow Dungeon", 10575, 10831);
		area("Shayzien Crypts", 6043);
		area("Sisterhood Sanctuary", 14999, 15000, 15001, 15255, 15256, 15257, 15511, 15512, 15513);
		area("Smoke Dungeon", 12946, 13202);
		area("Sophanem Dungeon", 13200);
		area("Sourhog Cave", 12695);
		area("Stronghold of Security", 7505, 8017, 8530, 9297);
		area("Stronghold Slayer Cave", 9624, 9625, 9880, 9881);
		area("Tarn's Lair", 12616, 12615);
		area("Taverley Dungeon", 11416, 11417, 11671, 11672, 11673, 11928, 11929);
		area("Temple of Ikov", 10649, 10905, 10650);
		area("Temple of Light", 7496);
		area("Temple of Marimbo", 11151);
		area("The Burrow", 6291);
		area("The Warrens", 7070, 7326);
		area("Dungeon of Tolna", 13209);
		area("Tower of Life Basement", 12100);
		area("Trahaearn Mine", 13250);
		area("Tunnel of Chaos", 12625);
		area("Underground Pass", 9369, 9370);
		area("Varrock Sewers", 12954, 13210);
		area("Viyeldi Caves", 9545, 11153);
		area("Warriors' Guild Basement", 11675);
		area("Water Ravine", 13461);
		area("Waterbirth Dungeon", 9886, 10142, 7492, 7748);
		area("Waterfall Dungeon", 10394);
		area("Werewolf Agility Course", 14234);
		area("White Wolf Mountain Caves", 11418, 11419);
		area("Witchhaven Shrine Dungeon", 10903);
		area("Wizards' Tower Basement", 12437);
		area("Woodcutting Guild Dungeon", 6298);
		area("Wyvern Cave", 14495, 14496);
		area("Yanille Agility Dungeon", 10388);
		// Cities and towns
		area("Al Kharid", 13105, 13106);
		area("Arceuus", 6458, 6459, 6460, 6714, 6715);
		area("Ardougne", 9779, 9780, 10035, 10036, 10291, 10292, 10547, 10548);
		area("Bandit Camp", 12590);
		area("Barbarian Outpost", 10039);
		area("Barbarian Village", 12341);
		area("Bedabin Camp", 12591);
		area("Brimhaven", 11057, 11058);
		area("Burgh de Rott", 13874, 13873, 14130, 14129);
		area("Burthorpe", 11319, 11575);
		area("Cam Torum", 5525, 5780, 5781, 6037);
		area("Canifis", 13878);
		area("Catherby", 11317, 11318, 11061);
		area("Civitas Illa Fortis", 6448, 6449, 6704, 6705, 6960, 6961);
		area("Corsair Cove", 10028, 10284);
		area("Darkmeyer", 14388, 14644);
		area("Dorgesh-Kaan", 10835, 10834);
		area("Draynor", 12338, 12339);
		area("Edgeville", 12342);
		area("Entrana", 11060, 11316);
		area("Etceteria", 10300);
		area("Falador", 11828, 11572, 11827, 12084);
		area("Gu'Tanoth", 10031);
		area("Gwenith", 8757);
		area("Hosidius", 6710, 6711, 6712, 6455, 6456, 6966, 6967, 6968, 7221, 7223, 7224, 7478, 7479);
		area("Jatizso", 9531);
		area("Keldagrim", 11423, 11422, 11679, 11678);
		area("Land's End", 5941);
		area("Lassar Undercity", 9314, 9315, 9316, 9571, 9572, 9828, 10338, 10339, 10340, 10596, 10852);
		area("Lletya", 9265, 11103);
		area("Lovakengj", 5692, 5691, 5947, 6203, 6202, 5690, 5946);
		area("Lumbridge", 12850);
		area("Lunar Isle", 8253, 8252, 8509, 8508);
		area("Meiyerditch", 14132, 14387, 14386, 14385);
		area("Menaphos", 12843);
		area("Miscellania", 10044);
		area("Mor Ul Rek", 9808, 9807, 10064, 10063);
		area("Mort'ton", 13875);
		area("Mos Le'Harmless", 14637, 14638, 14639, 14894, 14895, 15151, 15406, 15407);
		area("Mount Karuulm", 5179, 4923, 5180);
		area("Mountain Camp", 11065);
		area("Mynydd", 8501);
		area("Nardah", 13613);
		area("Neitiznot", 9275);
		area("Port Piscarilius", 6969, 6971, 7227, 6970, 7225, 7226);
		area("Piscatoris", 9273);
		area("Pollnivneach", 13358);
		area("Port Khazard", 10545);
		area("Port Phasmatys", 14646);
		area("Port Sarim", 12081, 12082);
		area("Prifddinas", 8499, 8500, 8755, 8756, 9011, 9012, 9013, 12894, 12895, 13150, 13151);
		area("Rellekka", 10297, 10553);
		area("Rimmington", 11826, 11570);
		area("Seers' Village", 10806);
		area("Shayzien", 5944, 5943, 6200, 6199, 5686, 5687, 5688, 5689, 5945);
		area("Shilo Village", 11310);
		area("Slepe", 14643, 14899, 14900, 14901);
		area("Sophanem", 13099);
		area("Tai Bwo Wannai", 11056, 11055);
		area("Taverley", 11574, 11573);
		area("Tree Gnome Stronghold", 9525, 9526, 9782, 9781);
		area("Tree Gnome Village", 10033);
		area("Troll Stronghold", 11321, 11421);
		area("Uzer", 13872);
		area("Uzer Oasis", 13871);
		area("Varrock", 12596, 12597, 12852, 12853, 12854, 13108, 13109, 13110);
		area("Ver Sinhaza", 14642);
		area("Void Knights' Outpost", 10537);
		area("Weiss", 11325, 11581);
		area("Witchaven", 10803);
		area("Yanille", 10288, 10032);
		area("Zanaris", 9285, 9541, 9540, 9797);
		area("Zul-Andra", 8495, 8751);
		// Wider regions
		area("Marim", 11051);
		area("Abyssal Area", 12108);
		area("Abyssal Nexus", 12106);
		area("Agility Pyramid", 12105, 13356);
		area("Air Altar", 11339);
		area("Al Kharid Mine", 13107);
		area("Ancient Vault", 12644, 13156);
		area("Ape Atoll", 10794, 10795, 10974, 11050);
		area("Arandar", 9266, 9267, 9523);
		area("Asgarnia", 11825, 11829, 11830, 12085, 12086);
		area("Avium Savannah", 5935, 5936, 5937, 6189, 6445, 6446, 6447, 6701, 6702, 6703, 6957, 6958, 6959, 7215);
		area("Battlefield", 10034);
		area("Battlefront", 5433, 5434);
		area("Blast Mine", 5948);
		area("Body Altar", 10059);
		area("Chaos Altar", 9035);
		area("Cosmic Altar", 8523);
		area("Cosmic Entity's Plane", 8267);
		area("Crabclaw Isle", 6965);
		area("Crafting Guild", 11571);
		area("Crandor", 11314, 11315);
		area("Crash Island", 11562);
		area("Dark Altar", 6716);
		area("Death Altar", 8779);
		area("Death Plateau", 11320);
		area("Dense Essence Mine", 6972);
		area("Desert Plateau", 13361, 13617);
		area("Digsite", 13365);
		area("Dragontooth Island", 15159);
		area("Draynor Manor", 12340);
		area("Drill Sergeant's Training Camp", 12619);
		area("Eagles' Peak", 9270);
		area("Earth Altar", 10571);
		area("Enchanted Valley", 12102);
		area("Evil Twin Crane Room", 7504);
		area("Exam Centre", 13364);
		area("Falador Farm", 12083);
		area("Farming Guild", 4922);
		area("Feldip Hills", 9773, 9774, 10029, 10030, 10285, 10286, 10287, 10542, 10543);
		area("Fenkenstrain's Castle", 14135);
		area("Fight Arena", 10289);
		area("Fire Altar", 10315);
		area("Fisher Realm", 10569);
		area("Fishing Guild", 10293);
		area("Fishing Platform", 11059);
		area("The Forsaken Tower", 5435);
		area("Fossil Island", 14650, 14651, 14652, 14906, 14907, 14908, 15162, 15163, 15164);
		area("Freaky Forester's Clearing", 10314);
		area("Fremennik Province", 10296, 10552, 10808, 10809, 10810, 10811, 11064);
		area("Fremennik Isles", 9276, 9532);
		area("Frogland", 9802);
		area("Galvek Shipwrecks", 6486, 6487, 6488, 6489, 6742, 6743, 6744, 6745);
		area("Ghorrock Dungeon", 11681);
		area("Gorak's Plane", 12115);
		area("Grand Exchange", 12598);
		area("God Wars Dungeon", 11578);
		area("Harmony Island", 15148);
		area("Hazelmere's Island", 10544);
		area("Hunter Guild", 6191);
		area("Ice Path", 11322, 11323);
		area("Iceberg", 10558, 10559);
		area("Icyene Graveyard", 14641, 14897, 14898);
		area("Isafdar", 8497, 8753, 8754, 9009, 9010);
		area("Island of Stone", 9790);
		area("Isle of Souls", 8236, 8237, 8238, 8491, 8492, 8494, 8747, 8750, 9003, 9004, 9006, 9260, 9261, 9262);
		area("Jiggig", 9775);
		area("Kandarin", 9268, 9269, 9014, 9263, 9264, 9519, 9524, 9527, 9776, 9783, 10037, 10290, 10294, 10546, 10551, 10805, 11062);
		area("Karamja", 10801, 10802, 11054, 11311, 11312, 11313, 11566, 11567, 11568, 11569, 11822);
		area("Kebos Lowlands", 4665, 4666, 4667, 4921, 5178);
		area("Kebos Swamp", 4664, 4920, 5174, 5175, 5176, 5430, 5431);
		area("Kharazi Jungle", 11053, 11309, 11565, 11821);
		area("Kharidian Desert", 12587, 12844, 12845, 12846, 12847, 12848, 13100, 13101, 13102, 13103, 13104, 13357, 13359, 13360, 13614, 13615, 13616, 13869, 13870);
		area("Killerwatt Plane", 10577);
		area("Great Kourend", 6201, 6457, 6713);
		area("Kourend Woodland", 5942, 6197, 6453);
		area("Law Altar", 9803);
		area("Legends' Guild", 10804);
		area("Lighthouse", 10040);
		area("Lithkren", 14142, 14398);
		area("Lumbridge Swamp", 12593, 12849);
		area("Max Island", 11063);
		area("McGrubor's Wood", 10550);
		area("Mime's Stage", 8010);
		area("Mind Altar", 11083);
		area("Misthalin", 12594, 12595, 12851);
		area("Molch", 5177);
		area("Molch Island", 5432);
		area("Morytania", 13619, 13620, 13621, 13622, 13876, 13877, 13879, 14133, 14134, 14389, 14390, 14391, 14645, 14647);
		area("Mount Quidamortem", 4662, 4663, 4918, 4919);
		area("Mr. Mordaut's Classroom", 7502);
		area("Mudskipper Point", 11824);
		area("Mysterious Old Man's Maze", 11591);
		area("Myths' Guild", 9772);
		area("Nature Altar", 9547);
		area("Necropolis", 13098, 13353, 13354, 13609, 13610);
		area("Northern Tundras", 6204, 6205, 6717);
		area("Observatory", 9777);
		area("Odd One Out", 7754);
		area("Ortus Farm", 6192, 6193);
		area("Otto's Grotto", 10038);
		area("Ourania Hunter Area", 9778);
		area("Pirates' Cove", 8763);
		area("Piscatoris Hunter Area", 9015, 9016, 9271, 9272, 9528);
		area("Player Owned House", 7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303);
		area("Poison Waste", 8752, 9008);
		area("Port Tyras", 8496);
		area("Puro-Puro", 10307);
		area("Quarry", 12589);
		area("Ralos' Rise", 5424, 5425, 5679, 5680, 5681, 5682);
		area("Ranging Guild", 10549);
		area("Ratcatchers Mansion", 11343);
		area("Ruins of Unkah", 12588);
		area("Ruins of Ullek", 13355, 13611, 13612);
		area("Rune Essence Mine", 11595);
		area("ScapeRune", 10058, 7758, 8261);
		area("Sea Spirit Dock", 12332);
		area("Ship Yard", 11823);
		area("Silvarea", 13366);
		area("Sinclair Mansion", 10807);
		area("Slayer Tower", 13623, 13723);
		area("Soul Altar", 7228);
		area("Stranglewood Temple", 4761);
		area("Sunset Coast", 5934, 6190);
		area("The Scar", 8036, 8292);
		area("The Stranglewood", 4403, 4404, 4659, 4660, 4661, 4916, 4917);
		area("Troll Arena", 11576);
		area("Trollheim", 11577);
		area("Trollweiss Mountain", 11066, 11067, 11068);
		area("Tutorial Island", 12079, 12080, 12335, 12336, 12436, 12592);
		area("Underwater", 15008, 15264);
		area("Water Altar", 10827);
		area("Waterbirth Island", 10042);
		area("Wintertodt Camp", 6461);
		area("Wizards' Tower", 12337);
		area("Woodcutting Guild", 6198, 6454);
		area("Wrath Altar", 9291);
	}

	private Areas()
	{
	}

	private static void area(String name, int... regions)
	{
		for (int region : regions)
		{
			BY_REGION.putIfAbsent(region, name);
		}
	}

	/** The place a map square is in, or null if it isn't a named one */
	static String name(int region, boolean instance)
	{
		String name = instance ? INSTANCES.get(region) : null;
		return name != null ? name : BY_REGION.get(region);
	}
}
