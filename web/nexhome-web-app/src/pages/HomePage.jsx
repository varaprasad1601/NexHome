import AboutSection from '../components/home/AboutSection';
import ContactSection from '../components/home/ContactSection';
import FaqSection from '../components/home/FaqSection';
import HeroSection from '../components/home/HeroSection';
import HowItWorksSection from '../components/home/HowItWorksSection';
import JoinSection from '../components/home/JoinSection';
import ServicesSection from '../components/home/ServicesSection';
import TeamSection from '../components/home/TeamSection';
import WhyChooseSection from '../components/home/WhyChooseSection';

/** The home page: every section in order. */
export default function HomePage() {
  return (
    <>
      <HeroSection />
      <AboutSection />
      <ServicesSection />
      <HowItWorksSection />
      <WhyChooseSection />
      <JoinSection />
      <TeamSection />
      <FaqSection />
      <ContactSection />
    </>
  );
}
