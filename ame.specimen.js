// Ame Mascot: a glossy droplet listening to music with retro-modern headphones,
// vibing to the beat with dual glowing sound rings on the earcups.

export const scene = {
	accent: '#8b5cf6', // electric violet (Ame brand accent)
	glow: '#a78bfa',
	duration: 8,
	fit: { scale: 0.86, x: 0, y: - 0.03 },
	lights: { tint: 0.45, underFill: 3 },
};

export function object( { THREE, mat, geo, TAU } ) {

	// 1. Droplet Mascot Body (Glossy lacquer / ceramic)
	// A plump, lovable droplet silhouette:
	// Apex at y = 0.46, tapering smoothly to round cheeks at y = -0.14,
	// rounding into a soft weighted base at y = -0.50.
	const dropletCurve = new THREE.SplineCurve( [
		new THREE.Vector2( 0.001, 0.46 ),
		new THREE.Vector2( 0.07, 0.40 ),
		new THREE.Vector2( 0.18, 0.26 ),
		new THREE.Vector2( 0.30, 0.08 ),
		new THREE.Vector2( 0.39, - 0.12 ),
		new THREE.Vector2( 0.41, - 0.28 ),
		new THREE.Vector2( 0.34, - 0.42 ),
		new THREE.Vector2( 0.18, - 0.49 ),
		new THREE.Vector2( 0.001, - 0.50 ),
	] );
	const bodyPoints = dropletCurve.getPoints( 80 );
	const bodyGeo = geo.lathe( bodyPoints.map( p => [ p.x, p.y ] ), 96 );
	const bodyMat = mat.body( { finish: 'gloss' } );
	const body = new THREE.Mesh( bodyGeo, bodyMat );

	// 2. Headphone Assembly
	const headphones = new THREE.Group();

	// Arch headband: smooth semi-elliptical crown + straight legs down to ear cups
	const R_X = 0.41, R_Y = 0.34, C_Y = 0.22, LEG_BOTTOM_Y = - 0.10;
	const headbandPath = new THREE.CurvePath();
	// Left straight leg
	headbandPath.add( new THREE.LineCurve3(
		new THREE.Vector3( - R_X, LEG_BOTTOM_Y, 0 ),
		new THREE.Vector3( - R_X, C_Y, 0 )
	) );
	// Upper semi-ellipse arch
	const arcPoints = [];
	for ( let i = 0; i <= 36; i ++ ) {

		const angle = Math.PI - ( i / 36 ) * Math.PI; // from PI (left) down to 0 (right)
		arcPoints.push( new THREE.Vector3(
			R_X * Math.cos( angle ),
			C_Y + R_Y * Math.sin( angle ),
			0
		) );

	}

	headbandPath.add( new THREE.CatmullRomCurve3( arcPoints ) );
	// Right straight leg
	headbandPath.add( new THREE.LineCurve3(
		new THREE.Vector3( R_X, C_Y, 0 ),
		new THREE.Vector3( R_X, LEG_BOTTOM_Y, 0 )
	) );

	const archGeo = new THREE.TubeGeometry( headbandPath, 96, 0.034, 16, false );
	const archMesh = new THREE.Mesh( archGeo, mat.chrome( { finish: 'brushed', finishRepeat: 4 } ) );
	headphones.add( archMesh );

	// Headband top cushion: matte comfort padding along the crown
	const padArcPoints = [];
	for ( let i = 0; i <= 24; i ++ ) {

		const angle = 0.72 * Math.PI - ( i / 24 ) * 0.44 * Math.PI; // covers the crown zone
		padArcPoints.push( new THREE.Vector3(
			R_X * Math.cos( angle ),
			C_Y + R_Y * Math.sin( angle ),
			0
		) );

	}

	const padCurve = new THREE.CatmullRomCurve3( padArcPoints );
	const padGeo = new THREE.TubeGeometry( padCurve, 32, 0.048, 16, false );
	const padMesh = new THREE.Mesh( padGeo, mat.body( { finish: 'matte' } ) );
	headphones.add( padMesh );

	// Ear Cups (Left and Right)
	const earGlowMat = mat.glow( 2.2 );
	const glowParts = [];

	function createEarCup( side ) {

		const cupGroup = new THREE.Group();
		const xPos = side * 0.41;
		cupGroup.position.set( xPos, - 0.10, 0 );

		// Plush memory foam ear cushion pressed to head
		const cushionTorus = new THREE.TorusGeometry( 0.13, 0.04, 16, 32 );
		cushionTorus.rotateY( Math.PI / 2 );
		const cushion = new THREE.Mesh( cushionTorus, mat.body( { finish: 'matte' } ) );
		cushion.position.x = - side * 0.015;
		cupGroup.add( cushion );

		// Outer shell housing (mirror chrome)
		const shellGeo = new THREE.CylinderGeometry( 0.175, 0.175, 0.065, 32 );
		shellGeo.rotateZ( Math.PI / 2 );
		const shell = new THREE.Mesh( shellGeo, mat.chrome() );
		shell.position.x = side * 0.035;
		cupGroup.add( shell );

		// Outer faceplate bevel (gloss body role)
		const faceGeo = new THREE.CylinderGeometry( 0.155, 0.155, 0.018, 32 );
		faceGeo.rotateZ( Math.PI / 2 );
		const face = new THREE.Mesh( faceGeo, mat.body( { finish: 'gloss' } ) );
		face.position.x = side * 0.072;
		cupGroup.add( face );

		// Outer glowing sound ring (sound wave ripple 1)
		const outerRingGeo = new THREE.TorusGeometry( 0.105, 0.018, 16, 48 );
		outerRingGeo.rotateY( Math.PI / 2 );
		const outerRing = new THREE.Mesh( outerRingGeo, earGlowMat );
		outerRing.position.x = side * 0.082;
		cupGroup.add( outerRing );
		glowParts.push( outerRing );

		// Inner glowing sound ring (sound wave ripple 2)
		const innerRingGeo = new THREE.TorusGeometry( 0.052, 0.013, 16, 36 );
		innerRingGeo.rotateY( Math.PI / 2 );
		const innerRing = new THREE.Mesh( innerRingGeo, earGlowMat );
		innerRing.position.x = side * 0.084;
		cupGroup.add( innerRing );
		glowParts.push( innerRing );

		// Central chrome hub bullet
		const hubGeo = new THREE.SphereGeometry( 0.028, 24, 16 );
		const hub = new THREE.Mesh( hubGeo, mat.chrome() );
		hub.position.x = side * 0.085;
		cupGroup.add( hub );

		// Gimbal yoke hinge connecting band to cup
		const hingeGeo = new THREE.TorusGeometry( 0.065, 0.015, 12, 24, Math.PI );
		hingeGeo.rotateZ( side > 0 ? 0 : Math.PI );
		const hinge = new THREE.Mesh( hingeGeo, mat.chrome() );
		hinge.position.set( side * 0.02, 0.08, 0 );
		cupGroup.add( hinge );

		return cupGroup;

	}

	headphones.add( createEarCup( 1 ) );  // Right cup (facing key light)
	headphones.add( createEarCup( - 1 ) ); // Left cup

	// 3. Cute Vibing Face: Smiling Eyes (^ ^) and Glowing Blush Cheeks
	const faceGroup = new THREE.Group();
	const eyeMat = mat.chrome(); // Glints brightly with studio specular highlights

	function createEye( x ) {

		// Closed smiling eye arc
		const eyeGeo = new THREE.TorusGeometry( 0.042, 0.011, 12, 24, Math.PI );
		const eye = new THREE.Mesh( eyeGeo, eyeMat );
		eye.position.set( x, - 0.04, 0.36 );
		eye.rotation.x = - 0.22;
		eye.rotation.z = Math.PI; // flip so arc smiles: ^
		return eye;

	}

	faceGroup.add( createEye( - 0.13 ) );
	faceGroup.add( createEye( 0.13 ) );

	// Soft glowing blush cheeks
	const blushMat = mat.glow( 0.9 );
	function createBlush( x ) {

		const blushGeo = new THREE.CircleGeometry( 0.042, 24 );
		const blush = new THREE.Mesh( blushGeo, blushMat );
		blush.position.set( x, - 0.10, 0.365 );
		blush.rotation.x = - 0.22;
		return blush;

	}

	const leftBlush = createBlush( - 0.20 );
	const rightBlush = createBlush( 0.20 );
	faceGroup.add( leftBlush, rightBlush );

	// Root Mascot Group
	const mascot = new THREE.Group();
	mascot.add( body, headphones, faceGroup );

	const mesh = new THREE.Group();
	mesh.add( mascot );
	mesh.position.set( 0, 0, 0.45 ); // float slightly above tile

	function update( phase ) {

		// 4 beats per 8-second loop (2s per beat: chill, relaxed groove)
		const beatPhase = ( phase * 4 ) * TAU;

		// Head bob: rhythmic vertical compression and bounce
		mascot.position.y = 0.024 * Math.sin( beatPhase );

		// Head tilt & nod: subtle rhythmic swagger
		mascot.rotation.z = 0.045 * Math.sin( phase * TAU * 2 );
		mascot.rotation.x = 0.05 + 0.025 * Math.sin( beatPhase );

		// Gentle yaw to catch key light on bevels and chrome
		mesh.rotation.y = - 0.22 + 0.09 * Math.sin( phase * TAU );

		// Musical pulse: glowing earcups pulse in time with the music beat
		const pulse = Math.pow( 0.5 + 0.5 * Math.sin( beatPhase - Math.PI / 2 ), 2 );
		earGlowMat.emissiveIntensity = 1.3 + 2.4 * pulse;
		blushMat.emissiveIntensity = 0.5 + 1.2 * pulse;

		for ( let i = 0; i < glowParts.length; i ++ ) {

			glowParts[ i ].scale.setScalar( 1 + 0.05 * pulse );

		}

	}

	return { mesh, update };

}