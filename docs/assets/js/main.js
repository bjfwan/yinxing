(() => {
  const revealSelectors = [
    "[data-hero-item]",
    "[data-hero-visual]",
    "[data-hero-photo]",
    "[data-reveal]",
    "[data-reveal-group] > *",
    "[data-screen-stage] > *"
  ]

  const revealEverything = () => {
    document.querySelectorAll(revealSelectors.join(",")).forEach(node => {
      node.style.removeProperty("opacity")
      node.style.removeProperty("visibility")
      node.style.removeProperty("transform")
    })
  }

  if (!window.gsap) {
    document.documentElement.dataset.motion = "fallback"
    revealEverything()
    return
  }

  const { gsap, ScrollTrigger } = window
  if (ScrollTrigger) {
    gsap.registerPlugin(ScrollTrigger)
  }
  document.documentElement.dataset.motion = `gsap-${gsap.version}`
  const media = gsap.matchMedia()

  media.add(
    {
      isDesktop: "(min-width: 768px)",
      isMobile: "(max-width: 767px)",
      scrollStory: "(min-width: 960px) and (min-height: 700px)",
      finePointer: "(hover: hover) and (pointer: fine)",
      reduceMotion: "(prefers-reduced-motion: reduce)"
    },
    context => {
      const { isDesktop, scrollStory, finePointer, reduceMotion } = context.conditions

      if (reduceMotion) {
        gsap.set(revealSelectors.join(","), { clearProps: "all" })
        return undefined
      }

      const heroTimeline = gsap.timeline({
        defaults: {
          duration: 0.78,
          ease: "power3.out"
        }
      })

      heroTimeline
        .addLabel("intro", 0)
        .from("[data-hero-item]", {
          autoAlpha: 0,
          y: 24,
          stagger: 0.08,
          clearProps: "transform,opacity,visibility"
        }, "intro")
        .from("[data-hero-visual]", {
          autoAlpha: 0,
          y: isDesktop ? 30 : 24,
          scale: 0.985,
          duration: 1,
          clearProps: "transform,opacity,visibility"
        }, "intro+=0.08")
        .from("[data-hero-photo]", {
          scale: 1.045,
          duration: 1.08,
          ease: "power2.out",
          clearProps: "transform"
        }, "intro+=0.08")

      const experience = document.querySelector("[data-scroll-story]")
      const storyProduct = experience?.querySelector("[data-story-product]")
      const screenStage = experience?.querySelector("[data-screen-stage]")
      const storyCards = experience ? [...experience.querySelectorAll("[data-story-screen]")] : []
      const storyCopies = experience ? [...experience.querySelectorAll("[data-story-copy]")] : []
      const hasScrollStory = Boolean(
        scrollStory
        && ScrollTrigger
        && experience
        && storyProduct
        && screenStage
        && storyCards.length === 3
        && storyCopies.length === 3
      )
      let scrollStoryTimeline
      let refreshFrame

      if (hasScrollStory) {
        const [homeScreen, settingsScreen, contactsScreen] = storyCards
        const [homeCopy, settingsCopy, contactsCopy] = storyCopies

        experience.classList.add("is-scroll-story")
        gsap.set(storyProduct, { autoAlpha: 1, xPercent: 0 })
        gsap.set(storyCards, {
          clipPath: "inset(0% 0% 0% 0%)",
          force3D: false
        })
        gsap.set(homeScreen, { zIndex: 1 })
        gsap.set(settingsScreen, {
          clipPath: "inset(100% 0% 0% 0%)",
          zIndex: 2
        })
        gsap.set(contactsScreen, {
          clipPath: "inset(100% 0% 0% 0%)",
          zIndex: 3
        })
        gsap.set(homeCopy, { autoAlpha: 1, y: 0 })
        gsap.set([settingsCopy, contactsCopy], { autoAlpha: 0, y: 28 })

        scrollStoryTimeline = gsap.timeline({
          defaults: {
            duration: 1,
            ease: "none"
          },
          scrollTrigger: {
            trigger: experience,
            start: "top top",
            end: "+=480%",
            pin: true,
            scrub: 1.15,
            anticipatePin: 1,
            invalidateOnRefresh: true
          }
        })

        scrollStoryTimeline
          .addLabel("home", 0)
          .to({}, { duration: 1.45 }, "home")
          .addLabel("settings", 1.45)
          .to(homeCopy, {
            y: -18,
            duration: 0.18,
            ease: "power2.in"
          }, "settings")
          .set(homeCopy, { autoAlpha: 0 }, "settings+=0.18")
          .set(settingsCopy, { autoAlpha: 1 }, "settings+=0.24")
          .to(settingsCopy, {
            y: 0,
            duration: 0.32,
            ease: "power2.out"
          }, "settings+=0.24")
          .to(settingsScreen, {
            clipPath: "inset(0% 0% 0% 0%)",
            duration: 0.72,
            ease: "power2.inOut"
          }, "settings")
          .to({}, { duration: 1.3 }, "settings+=0.72")
          .addLabel("contacts", 3.47)
          .to(settingsCopy, {
            y: -18,
            duration: 0.18,
            ease: "power2.in"
          }, "contacts")
          .set(settingsCopy, { autoAlpha: 0 }, "contacts+=0.18")
          .set(contactsCopy, { autoAlpha: 1 }, "contacts+=0.24")
          .to(contactsCopy, {
            y: 0,
            duration: 0.32,
            ease: "power2.out"
          }, "contacts+=0.24")
          .to(contactsScreen, {
            clipPath: "inset(0% 0% 0% 0%)",
            duration: 0.72,
            ease: "power2.inOut"
          }, "contacts")
          .to({}, { duration: 1.55 }, "contacts+=0.72")

        refreshFrame = requestAnimationFrame(() => ScrollTrigger.refresh())
      }

      const cleanupScrollStory = () => {
        if (refreshFrame) {
          cancelAnimationFrame(refreshFrame)
        }

        experience?.classList.remove("is-scroll-story")
      }

      const tiltCleanups = []

      if (isDesktop && finePointer && !hasScrollStory) {
        document.querySelectorAll("[data-tilt-card]").forEach(card => {
          const tiltSurface = card.querySelector("img") ?? card
          const rotateXTo = gsap.quickTo(tiltSurface, "rotationX", { duration: 0.5, ease: "power3.out" })
          const rotateYTo = gsap.quickTo(tiltSurface, "rotationY", { duration: 0.5, ease: "power3.out" })
          const xTo = gsap.quickTo(tiltSurface, "x", { duration: 0.5, ease: "power3.out" })
          const yTo = gsap.quickTo(tiltSurface, "y", { duration: 0.5, ease: "power3.out" })

          gsap.set(tiltSurface, { transformPerspective: 1100, transformOrigin: "center" })

          const move = event => {
            const bounds = tiltSurface.getBoundingClientRect()
            const x = (event.clientX - bounds.left) / bounds.width - 0.5
            const y = (event.clientY - bounds.top) / bounds.height - 0.5

            rotateXTo(-y * 5)
            rotateYTo(x * 6)
            xTo(x * 4)
            yTo(y * 4)
            card.style.setProperty("--tilt-glare-x", `${(x + 0.5) * 100}%`)
            card.style.setProperty("--tilt-glare-y", `${(y + 0.5) * 100}%`)
          }

          const leave = () => {
            rotateXTo(0)
            rotateYTo(0)
            xTo(0)
            yTo(0)
            card.style.setProperty("--tilt-glare-x", "50%")
            card.style.setProperty("--tilt-glare-y", "50%")
          }

          card.addEventListener("pointermove", move, { passive: true })
          card.addEventListener("pointerleave", leave)
          tiltCleanups.push(() => {
            card.removeEventListener("pointermove", move)
            card.removeEventListener("pointerleave", leave)
            gsap.killTweensOf(tiltSurface)
            gsap.set(tiltSurface, { clearProps: "transform" })
          })
        })
      }

      const cleanupTilt = () => tiltCleanups.forEach(cleanup => cleanup())

      const revealNodes = [...document.querySelectorAll("[data-reveal], [data-reveal-group], [data-screen-stage]")]
        .filter(node => !(hasScrollStory && experience.contains(node)))

      if (!("IntersectionObserver" in window)) {
        return () => {
          cleanupScrollStory()
          cleanupTilt()
          heroTimeline.kill()
        }
      }

      revealNodes.forEach(node => {
        if (node.hasAttribute("data-reveal-group")) {
          gsap.set(node.children, { autoAlpha: 0, y: 30 })
          return
        }

        if (node.hasAttribute("data-screen-stage")) {
          gsap.set(node.children, { autoAlpha: 0, scale: 0.96 })
          return
        }

        gsap.set(node, { autoAlpha: 0, y: 34 })
      })

      const observer = new IntersectionObserver(
        entries => {
          entries.forEach(entry => {
            if (!entry.isIntersecting) {
              return
            }

            const node = entry.target

            if (node.hasAttribute("data-reveal-group")) {
              gsap.to(node.children, {
                autoAlpha: 1,
                y: 0,
                duration: 0.7,
                ease: "power3.out",
                stagger: 0.1,
                clearProps: "transform,opacity,visibility"
              })
            } else if (node.hasAttribute("data-screen-stage")) {
              gsap.to(node.children, {
                autoAlpha: 1,
                scale: 1,
                duration: 0.82,
                ease: "power3.out",
                stagger: 0.12,
                clearProps: "transform,opacity,visibility"
              })
            } else {
              gsap.to(node, {
                autoAlpha: 1,
                y: 0,
                duration: 0.76,
                ease: "power3.out",
                clearProps: "transform,opacity,visibility"
              })
            }

            observer.unobserve(node)
          })
        },
        {
          threshold: 0.14,
          rootMargin: "0px 0px -8% 0px"
        }
      )

      revealNodes.forEach(node => observer.observe(node))

      return () => {
        observer.disconnect()
        cleanupScrollStory()
        cleanupTilt()
        heroTimeline.kill()
      }
    }
  )

  window.addEventListener("pagehide", () => media.revert(), { once: true })
})()
